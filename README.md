# MapKit for Android

English | [日本語](README.ja.md)

Kotlin library that bridges MapKit JS on Android `WebView`.
You can control region, annotations, overlays, and map options through Kotlin models and `MKMapView` for Compose.

## Install (JitPack, v0.5.0)

### 1. Add JitPack to `settings.gradle.kts`

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

### 2. Add dependency

Recommended: single aggregate artifact.

```kotlin
dependencies {
    implementation("com.github.kmatsushita1012:mapkit-android:v0.5.0")
}
```

You can also use per-module artifacts:

```kotlin
dependencies {
    implementation("com.github.kmatsushita1012.mapkit-android:mapkit-core:v0.5.0")
    implementation("com.github.kmatsushita1012.mapkit-android:mapkit-webview:v0.5.0")
    implementation("com.github.kmatsushita1012.mapkit-android:mapkit-compose:v0.5.0")
}
```

## Prerequisites

### 1. Prepare Apple MapKit JS token

Generate a MapKit JS JWT and inject it at app startup via `MKMapKit.init(token, config)`.

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

`local.properties` example:

```properties
MAPKIT_JS_TOKEN=YOUR_MAPKIT_JS_JWT
MAPKIT_JS_WEB_DOMAIN=maps.example.com
```

`build.gradle.kts` example to load token from `local.properties` with env-var fallback:

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

See [docs/setup/mapkitjs-token-env.md](docs/setup/mapkitjs-token-env.md) for details.

### 2. Android permissions / manifest

At minimum:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

If `showsUserLocation` is enabled, runtime location permission is also required.

## Minimal usage (Compose)

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

## Adjusted region resolver

If you need the same corrected region for Apple Snapshot rendering and Android-side projection, use `MKRegionAdjuster`.

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

Recommended usage is to inject `MKRegionAdjuster` into your ViewModel while keeping `ActivityBoundMKRegionAdjuster` creation in the UI or DI layer.

## Modules

- `source/mapkit-android`: aggregate artifact for easier adoption
- `source/mapkit-android-core`: public models and init API
- `source/mapkit-android-webview`: WebView + MapKit JS bridge
- `source/mapkit-android-compose`: Compose API (`MKMapView`)
- `example/app`: sample app
