# MapKit for Android

[English](README.md) | 日本語

MapKit JS を Android `WebView` で扱うための Kotlin ライブラリです。  
Compose から `MKMapView` を使って地図を表示し、Annotation / Overlay / Region / Option を Kotlin モデルで制御できます。

## インストール (JitPack, v0.6.0)

### 1. `settings.gradle.kts` に JitPack を追加

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

### 2. 依存を追加

推奨は単一artifactです。

```kotlin
dependencies {
    implementation("com.github.kmatsushita1012:mapkit-android:v0.6.0")
}
```

必要に応じて個別モジュールも選択できます。

```kotlin
dependencies {
    implementation("com.github.kmatsushita1012.mapkit-android:mapkit-core:v0.6.0")
    implementation("com.github.kmatsushita1012.mapkit-android:mapkit-webview:v0.6.0")
    implementation("com.github.kmatsushita1012.mapkit-android:mapkit-compose:v0.6.0")
}
```

## 事前準備

### 1. Apple MapKit JS Token を用意

MapKit JS 用 JWT token を発行し、アプリ起動時に `MKMapKit.init(token, config)` で注入します。

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MKMapKit.init(
            token = BuildConfig.MAPKIT_JS_TOKEN,
            config = MKMapKitConfig(
                webDomain = BuildConfig.MAPKIT_JS_WEB_DOMAIN
            )
        )

        setContent { App() }
    }
}
```

`local.properties` 例:

```properties
MAPKIT_JS_TOKEN=YOUR_MAPKIT_JS_JWT
MAPKIT_JS_WEB_DOMAIN=maps.example.com
```

`build.gradle.kts` で `local.properties` と環境変数をフォールバックして `BuildConfig` に渡す例:

```kotlin
import java.util.Properties

android {
    defaultConfig {
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use(::load)
        }
        val mapToken = localProps.getProperty("MAPKIT_JS_TOKEN")
            ?: System.getenv("MAPKIT_JS_TOKEN")
            ?: "DUMMY_TOKEN_FOR_DEMO"
        val mapWebDomain = localProps.getProperty("MAPKIT_JS_WEB_DOMAIN")
            ?: System.getenv("MAPKIT_JS_WEB_DOMAIN")
            ?: "appassets.androidplatform.net"
        val escapedMapToken = mapToken
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "MAPKIT_JS_TOKEN", "\"$escapedMapToken\"")
        buildConfigField("String", "MAPKIT_JS_WEB_DOMAIN", "\"$mapWebDomain\"")
    }
}
```

トークン設定の詳細は [docs/setup/mapkitjs-token-env.md](docs/setup/mapkitjs-token-env.md) を参照してください。

### 2. Android permission / manifest

最低限、以下を宣言してください。

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

`showsUserLocation` を有効化する場合は runtime permission の許可も必要です。

## 最小利用例 (Compose)

```kotlin
@Composable
fun SampleMap() {
    var state by remember {
        mutableStateOf(
            MKMapState(
                region = MKCoordinateRegion.fromCenter(
                    center = MKCoordinate(35.681236, 139.767125),
                    latitudeDelta = 0.05,
                    longitudeDelta = 0.05
                ),
                options = MKMapOptions(
                    mapStyle = MKMapStyle.standard,
                    userLocation = MKUserLocationOptions(isEnabled = false)
                )
            )
        )
    }

    MKMapView(
        state = state,
        onEvent = { event ->
            when (event) {
                is MKMapEvent.RegionDidChange -> {
                    if (event.settled) {
                        state = state.copy(region = event.region)
                    }
                }
                else -> Unit
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

## 補正 region resolver

Apple Snapshot 側と Android 側の投影で同じ補正後 region を使いたい場合は `MKRegionAdjuster` を利用します。

```kotlin
val adjuster: MKRegionAdjuster = ActivityBoundMKRegionAdjuster { activity }

val adjusted = adjuster.adjust(
    MKRegionAdjustmentRequest(
        region = rawRegion,
        widthPx = 594,
        heightPx = 420
    )
)
```

推奨は、ViewModel には `MKRegionAdjuster` を注入し、`ActivityBoundMKRegionAdjuster` の生成は UI 層または DI 層に閉じ込める構成です。

## モジュール構成

- `source/mapkit-android`: 導入を簡単にする集約モジュール
- `source/mapkit-android-core`: 公開モデル/初期化 API
- `source/mapkit-android-webview`: WebView + MapKit JS bridge
- `source/mapkit-android-compose`: Compose 向け API (`MKMapView`)
- `example/app`: サンプルアプリ
