# Android向け MapKit JS 連携基盤 具体仕様書

## 0. 前提

本仕様書は [main.md](/Users/matsushitakazuya/private/mapkit-android/docs/design/main.md) を具体化したものであり、各章を必ず **外部仕様(interface) -> 内部仕様** の順で記述する。

---

## 1. MapKit JS ドキュメントサマリ

### 1.1 外部仕様(interface)

#### 1.1.1 役割
- Apple Maps を Web 上で扱うための JavaScript API。
- `mapkit.init` で初期化し、`new mapkit.Map(...)` で地図インスタンスを生成する。
- 表示領域、注釈(Annotation)、重ね描画(Overlay)、イベント購読を API で操作する。

#### 1.1.2 本ライブラリ利用者に公開する考え方
- Kotlin 利用者は MapKit JS の API を直接触らない。
- 利用者は Kotlin の `MapState` と `MapEvent` を扱う。
- 表示意図は宣言的に渡し、実描画の詳細はライブラリ内部が吸収する。

#### 1.1.3 初期化で必要なもの
- Apple Maps 利用用トークン(JWT)。
- Android 側公開 API では `MapKitTokenProvider` 経由で供給する。
- トークン値は環境変数由来とし、ソースに直書きしない。

### 1.2 内部仕様

#### 1.2.1 WebView + JS 実装方針
- `WebView` 内で MapKit JS を読み込み、Bridge 経由で Kotlin と通信する。
- JS 側は `MapEngine` を単一エントリにし、以下を担当する。
- `applyMapProps`: map 基本設定反映
- `applyRegion`: 表示領域反映
- `applyAnnotationsDiff`: 注釈差分反映
- `applyOverlaysDiff`: overlay 差分反映
- `emitEvent`: Kotlin へイベント通知

#### 1.2.2 トークン供給
- Kotlin -> JS 初期化時に `authorizationCallback` へ token を橋渡し。
- token 取得失敗時は `MapError.TokenUnavailable` を返し、地図を未初期化状態で保持。

---

## 2. Map 全体仕様

### 2.1 外部仕様(interface)

#### 2.1.1 公開コンポーネント
- `MKMapView(state: MKMapState, onEvent: (MKMapEvent) -> Unit, modifier: Modifier = Modifier)`

#### 2.1.2 公開データモデル
- `MKMapState`
- `region: MKCoordinateRegion`
- `annotations: List<MKAnnotation>`
- `overlays: List<MKOverlay>`
- `options: MKMapOptions`

#### 2.1.3 公開イベント
- `MKMapEvent.MapLoaded`
- `MKMapEvent.MapError`
- `MKMapEvent.RegionDidChange`
- `MKMapEvent.MapTapped`
- `MKMapEvent.LongPress`
- `MKMapEvent.AnnotationTapped`
- `MKMapEvent.AnnotationDeselected`
- `MKMapEvent.OverlayTapped`
- `MKMapEvent.UserLocationUpdated`

#### 2.1.4 動作契約
- `state` は差分適用される(全面再生成しない)。
- ユーザー操作中は領域を押し戻さない。
- `onEvent` は UI 詳細ではなく意味イベントのみ通知する。

### 2.2 内部仕様

#### 2.2.1 層構成
- App 層: Presenter/StateFlow/Compose
- Bridge 層: Kotlin <-> JS 変換、差分計算、ループ抑止
- Engine 層: WebView 上 MapKit JS

#### 2.2.2 同期アルゴリズム
- 描画は `MKMapState` 差分で反映する。
- 選択制御は `MKMapState.selectAnnotation/deselectAnnotation` の command channel で反映する。
- command channel は state インスタンス寿命に依存しない共有チャネルを使い、dispatcher 未接続時は queue する。

#### 2.2.3 エラー処理
- JS 初期化失敗、token 不正、Bridge 変換失敗を `MapEvent.MapError` で返す。
- Bridge は復旧可能エラー時に再試行(指数バックオフ)を行う。

---

## 3. CoordinateRegion 等の描画系仕様

### 3.1 外部仕様(interface)

#### 3.1.1 公開モデル
- `CoordinateRegion`
- `center: GeoCoordinate` (`latitude`, `longitude`)
- `span: CoordinateSpan` (`latitudeDelta`, `longitudeDelta`)
- `lastOperation: RegionOperation` (`interactive` | `system`)
- `revision: Long`
- `isSettled: Boolean`

#### 3.1.2 公開操作
- `MapCommand.SetRegion(region, animated)`
- `MapCommand.FitBounds(points, edgePadding, animated)`
- `MapCommand.SetCenter(center, zoomHint, animated)` (簡易操作)
- `MKRegionAdjuster.adjust(request)` で、指定 region を Apple 描画系へ通した補正後 region を取得できる。

#### 3.1.3 公開イベント契約
- パン/ズーム中: `RegionDidChange(isSettled=false)`
- ジェスチャ終了後: `RegionDidChangeSettled(isSettled=true)`
- `revision` は system 更新の自己反映識別に使う。

### 3.2 内部仕様

#### 3.2.1 変換
- `CoordinateRegion` <-> MapKit JS `region` / `camera` を相互変換。
- 比較は `epsilon` による近似比較:
- `lat/lng`: 1e-6
- `delta`: 1e-5

#### 3.2.2 反映抑止
- JS 反映時に `pendingRevision` を記録。
- JS イベント受信時、同 revision かつ近似一致なら Kotlin への再反映を抑止。

#### 3.2.3 settled 判定
- `region-change-start` で `isInteracting = true`
- `region-change-end` で `isInteracting = false`, `isSettled = true`
- 途中イベントは throttling(例: 100ms)で Kotlin へ通知。

#### 3.2.4 補正 Region Resolver
- `MKRegionAdjuster` は最適表示領域の再計算ではなく、指定済み `region` を MapKit JS が最終的にどう解釈したかを返す。
- 実装は `Activity` 上の一時非表示 `WebView` で行い、`widthPx x heightPx` を実レイアウトサイズとして `mapkit.Map` を生成する。
- 補正後 region の取得は `region-change-end` を第一候補とし、初期適用時の未発火に備えて `requestAnimationFrame` による連続フレーム安定化を併用する。
- ViewModel は `Activity` を直接保持せず、`MKRegionAdjuster` 抽象に依存する。

---

## 4. Annotation / Overlay 仕様

### 4.1 外部仕様(interface)

#### 4.1.1 Annotation
- `MapAnnotation`
- `id: String`
- `coordinate: GeoCoordinate`
- `title: String?`
- `subtitle: String?`
- `isVisible: Boolean`
- `isSelected: Boolean` (getter only)
- `style: AnnotationStyle`

#### 4.1.2 Overlay
- `MapOverlay`(sealed)
- `PolylineOverlay(id, points, style, isVisible, zIndex)`
- `PolygonOverlay(id, points, holes, style, isVisible, zIndex)`
- `CircleOverlay(id, center, radiusMeter, style, isVisible, zIndex)`

#### 4.1.3 スタイル
- Annotation:
- `Default(tint, glyphText?, glyphImageSource?)` (MKMarkerAnnotationView 相当)
- `CustomImage(source, width, height, anchor)` (MKAnnotationView 相当)
- `Dom(html, anchorX, anchorY, allowSelection)` (MapKit JS `Annotation` + `HTMLElement` factory)
- Overlay:
- `strokeColor`, `strokeWidth`, `fillColor`, `lineDashPattern`
- 注記: Polygon/Circle の `fillColor` alpha は MapKit JS レンダラ依存で、
  alpha=1.0 相当でも完全不透明に見えない場合がある。

#### 4.1.4 イベント
- `AnnotationTapped(id)`
- `AnnotationDeselected(id)`
- `OverlayTapped(id)`

### 4.2 内部仕様

#### 4.2.1 差分反映
- `id` 単位で `add/update/remove` を計算。
- update 判定は `hash(style + geometry + visibility)` で実施。
- `isSelected` は差分キーに含めない。選択状態は select/deselect event 起点で同期する。
- map 再生成は禁止。差分命令のみ送信。
- `Dom` annotation の HTML 本文変更のみは `innerHTML` 差し替えで反映し、annotation 再生成を避ける。

#### 4.2.2 選択制御方針
- Kotlin 側の明示制御は `MKMapState.selectAnnotation/deselectAnnotation` を使う。
- JS 側の選択操作は `map.selectedAnnotation` の代入で行う。
- `map.selectAnnotation/map.deselectAnnotation` は使用しない。
- `isSelected` 更新と Kotlin 通知は `select` / `deselect` event listener のみで行う。

#### 4.2.2 Renderer 解決
- Annotation `style` ごとに renderer を選択:
- default renderer
- custom renderer registry (`key` 解決)
- 未登録 `key` はフォールバックで `DefaultMarker`。
- `Dom` は `mapkit.Annotation` の factory から `HTMLElement` を返して描画する。

#### 4.2.3 タップヒット処理
- JS 側でオブジェクト `id` を保持し、イベント時に Kotlin へ返却。
- 複数候補時は zIndex 優先で 1 件返す。

---

## 5. その他オプション仕様(地図スタイル等)

### 5.1 外部仕様(interface)

#### 5.1.1 公開オプション
- `MapOptions`
- `mapStyle: MapStyle` (`standard` | `mutedStandard` | `satellite` | `hybrid`)
- `showsCompass: Boolean`
- `showsScale: Boolean`
- `showsZoomControl: Boolean`
- `showsMapTypeControl: Boolean`
- `showsPointsOfInterest: Boolean`
- `poiFilter: PoiFilter`
- `isRotateEnabled: Boolean`
- `isScrollEnabled: Boolean`
- `isZoomEnabled: Boolean`
- `isPitchEnabled: Boolean`
- `appearance: AppearanceOption` (`auto` | `light` | `dark`)
- `language: MapLanguage` (`auto` | `ja` | `en`)
- `userLocation: UserLocationOptions` (`isEnabled`, `followsHeading`, `showsAccuracyRing`)

#### 5.1.2 非対応項目
- `showsTraffic` と `NavigationEmphasis` は公開 API から除外する。
- 理由: Android WebView + MapKit JS での再現性が安定しないため。
- 将来再導入する場合は `feature_request` で再定義してから検討する。

#### 5.1.3 配布/設定要件
- 利用者は環境変数で token 等を設定し、`local.properties` で参照する。
- 機密値を VCS にコミットしない。
- 成果物は Maven Central 公開前提。
- リポジトリは `source/`(ライブラリ) と `example/`(デモアプリ) を分離。

### 5.2 内部仕様

#### 5.2.1 MapKit JS へのマッピング
- `mapStyle -> map.mapType` 系へ変換
- UI 表示系(compass/scale/zoom/mapTypeControl)は map control 設定へ変換
- `poiFilter` は `pointOfInterestFilter` に変換
  - `none` は `PointOfInterestFilter.excludingAllCategories` を利用し、POI を完全非表示にする
- `userLocation` は `showsUserLocation` と WebView geolocation 許可フローに変換

#### 5.2.2 適用順序
- 初期化直後に `MapOptions` 一括適用。
- 以後は変更差分のみ適用し、再初期化しない。

#### 5.2.3 モジュール構成
- `source:mapkit-android` (利用者向け集約 artifact)
- `source:mapkit-android-core` (公開 API)
- `source:mapkit-android-webview` (Bridge/Engine)
- `source:mapkit-android-compose` (Compose ラッパ)
- `example:app` (利用例)

#### 5.2.4 公開設定
- `maven-publish` + `signing` を前提。
- `group/artifact/version` を固定管理し、`-SNAPSHOT` と release を分離。

---

## 6. 初期リリース受け入れ条件

- CoordinateRegion 同期でループが発生しない。
- interactive 操作中の押し戻しが発生しない。
- Annotation/Overlay が差分更新される。
- Default 表示 + Custom フックが動作する。
- mapStyle / zoomControl / poiFilter / userLocation が反映される。
- token 未設定時に明示的エラーが返る。
- `source` と `example` が分離され、example で動作確認可能。
