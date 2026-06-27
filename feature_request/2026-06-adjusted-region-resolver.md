# MapKit JS 補正 Region Resolver

## 目的
- 指定した `center + span + widthPx + heightPx` を MapKit JS に通し、Apple の描画系が最終的に解釈した補正後 `region` を返す。
- Snapshot API に渡す `center/spn` と Android 側のローカル投影計算に同じ region を使えるようにする。

## スコープ
- 対象は region 解決のみ。
- overlays / annotations からの最適表示計算は行わない。
- Snapshot API 呼び出しや Android 側描画は担当しない。

## 公開方針
- `MKMapView` とは分離した独立 API にする。
- ViewModel は `MKRegionAdjuster` 抽象へ依存し、UI 層で `ActivityBoundMKRegionAdjuster` を注入する。

## 実装方針
- `Activity` 上に一時的な非表示 `WebView` を生成し、指定サイズで MapKit JS を初期化する。
- 入力 region を適用した後、`region-change-end` または連続フレーム安定化で補正後 `map.region` を取得する。
- 取得結果は `MKCoordinateRegion` として返却する。
