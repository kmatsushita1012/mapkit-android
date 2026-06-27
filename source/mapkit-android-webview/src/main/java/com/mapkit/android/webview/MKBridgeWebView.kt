package com.studiomk.mapkit.webview

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.studiomk.mapkit.model.MKCoordinate
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKCircleOverlay
import com.studiomk.mapkit.model.MKMapKitConfig
import com.studiomk.mapkit.model.MKMapErrorCause
import com.studiomk.mapkit.model.MKMapCommand
import com.studiomk.mapkit.model.MKMapEvent
import com.studiomk.mapkit.model.MKMapRenderState
import com.studiomk.mapkit.model.MKRegionResolutionException
import com.studiomk.mapkit.model.MKAnnotationStyle
import com.studiomk.mapkit.model.MKImageSource
import com.studiomk.mapkit.model.MKPoiFilter
import com.studiomk.mapkit.model.MKPolygonOverlay
import com.studiomk.mapkit.model.MKPolylineOverlay
import com.studiomk.mapkit.model.MKWebAssetSource
import com.studiomk.mapkit.webview.internal.InternalMapState
import com.studiomk.mapkit.webview.internal.MKBridgeMapper
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
class MKBridgeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    mapKitConfig: MKMapKitConfig = MKMapKitConfig()
) : WebView(context, attrs) {
    companion object {
        private const val LOG_TAG = "MKBridgeWebView"
    }

    private var mapKitConfig: MKMapKitConfig = mapKitConfig
    private var assetLoader: WebViewAssetLoader = buildAssetLoader(mapKitConfig)

    private var onEvent: ((MKMapEvent) -> Unit)? = null
    private var isPageReady = false
    private var isJsInitSent = false
    private var pendingToken: String? = null
    private var lastAppliedRegionPayload: String? = null
    private var lastAppliedStatePayload: String? = null
    private var pendingState: MKMapRenderState? = null
    private val pendingCommands: MutableList<MKMapCommand> = mutableListOf()
    private var onResolvedRegion: ((Result<MKCoordinateRegion>) -> Unit)? = null

    private val androidBridge = object {
        @JavascriptInterface
        fun emitEvent(payload: String) {
            try {
                val event = parseEvent(JSONObject(payload))
                post { onEvent?.invoke(event) }
            } catch (t: Throwable) {
                post {
                    onEvent?.invoke(
                        MKMapEvent.MapError(
                            MKMapErrorCause.BridgeFailure("Failed to parse JS event: ${t.message}")
                        )
                    )
                }
            }
        }

        @JavascriptInterface
        fun emitRegionResolution(payload: String) {
            try {
                val result = parseRegionResolution(JSONObject(payload))
                post {
                    val callback = onResolvedRegion ?: return@post
                    onResolvedRegion = null
                    callback(result)
                }
            } catch (t: Throwable) {
                post {
                    val callback = onResolvedRegion ?: return@post
                    onResolvedRegion = null
                    callback(
                        Result.failure(
                            MKRegionResolutionException.BridgeFailure(
                                detail = "Failed to parse region resolution: ${t.message}",
                                source = t
                            )
                        )
                    )
                }
            }
        }
    }

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setGeolocationEnabled(true)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        isLongClickable = false
        setOnLongClickListener { true }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    LOG_TAG,
                    "console[${consoleMessage.messageLevel()}] ${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                )
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val granted = hasLocationPermission(context)
                callback?.invoke(origin, granted, false)
                if (!granted) {
                    onEvent?.invoke(
                        MKMapEvent.MapError(
                            MKMapErrorCause.BridgeFailure(
                                "Location permission not granted for geolocation origin=$origin"
                            )
                        )
                    )
                }
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isPageReady = true
                sendInitIfPossible()
                flushPendingState()
                flushPendingCommands()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                Log.e(
                    LOG_TAG,
                    "web error url=${request.url} code=${error.errorCode} desc=${error.description}"
                )
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                Log.e(
                    LOG_TAG,
                    "http error url=${request.url} status=${errorResponse.statusCode}"
                )
            }
        }
        addJavascriptInterface(androidBridge, "AndroidMKBridge")
        loadUrl(entryUrl(mapKitConfig))
    }

    fun setEventListener(listener: (MKMapEvent) -> Unit) {
        this.onEvent = listener
    }

    fun ensureInitialized(token: String) {
        pendingToken = token
        sendInitIfPossible()
    }

    fun applyMapKitConfig(config: MKMapKitConfig) {
        if (mapKitConfig == config) return
        mapKitConfig = config
        assetLoader = buildAssetLoader(config)
        isPageReady = false
        isJsInitSent = false
        lastAppliedRegionPayload = null
        lastAppliedStatePayload = null
        onResolvedRegion = null
        loadUrl(entryUrl(config))
    }

    fun applyState(state: MKMapRenderState) {
        pendingState = state
        if (!isPageReady || !isJsInitSent) return
        flushPendingState()
        flushPendingCommands()
    }

    fun applyCommand(command: MKMapCommand) {
        if (!isPageReady || !isJsInitSent) {
            pendingCommands += command
            return
        }
        when (command) {
            is MKMapCommand.SelectAnnotation -> {
                selectAnnotation(command.annotationId, command.animated)
            }
            is MKMapCommand.DeselectAnnotation -> {
                deselectAnnotation(command.animated)
            }
        }
    }

    fun selectAnnotation(annotationId: String, animated: Boolean = true) {
        val escapedId = JSONObject.quote(annotationId)
        evaluateJavascriptSafe(
            "window.MKBridge && window.MKBridge.selectAnnotationById && window.MKBridge.selectAnnotationById($escapedId, $animated);"
        )
    }

    fun deselectAnnotation(animated: Boolean = false) {
        evaluateJavascriptSafe(
            "window.MKBridge && window.MKBridge.deselectAnnotation && window.MKBridge.deselectAnnotation($animated);"
        )
    }

    fun resolveAdjustedRegion(region: MKCoordinateRegion, listener: (Result<MKCoordinateRegion>) -> Unit) {
        if (!isPageReady || !isJsInitSent) {
            listener(
                Result.failure(
                    MKRegionResolutionException.BridgeFailure("MKBridgeWebView is not ready for region resolution")
                )
            )
            return
        }
        if (onResolvedRegion != null) {
            listener(
                Result.failure(
                    MKRegionResolutionException.BridgeFailure("A region resolution is already in progress")
                )
            )
            return
        }
        onResolvedRegion = listener
        val payload = serializeRegion(region)
        evaluateJavascriptSafe(
            "window.MKBridge && window.MKBridge.resolveAdjustedRegion && window.MKBridge.resolveAdjustedRegion($payload);"
        )
    }

    private fun flushPendingState() {
        val latest = pendingState ?: return
        if (!isPageReady || !isJsInitSent) return

        val regionPayload = serializeRegion(latest.region)
        if (lastAppliedRegionPayload != regionPayload) {
            evaluateJavascriptSafe("window.MKBridge && window.MKBridge.applyRegion && window.MKBridge.applyRegion($regionPayload);")
            lastAppliedRegionPayload = regionPayload
        }

        val statePayload = serializeStateWithoutRegion(latest)
        if (lastAppliedStatePayload == statePayload) return
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.applyState($statePayload);")
        lastAppliedStatePayload = statePayload
    }

    private fun sendInitIfPossible() {
        if (!isPageReady || isJsInitSent) return
        val token = pendingToken ?: return
        val escaped = JSONObject.quote(token)
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.init($escaped);")
        isJsInitSent = true
    }

    private fun flushPendingCommands() {
        if (!isPageReady || !isJsInitSent || pendingCommands.isEmpty()) return
        val commands = pendingCommands.toList()
        pendingCommands.clear()
        commands.forEach { applyCommand(it) }
    }

    private fun evaluateJavascriptSafe(script: String) {
        evaluateJavascript(script, ValueCallback { })
    }

    private fun buildAssetLoader(config: MKMapKitConfig): WebViewAssetLoader {
        val builder = WebViewAssetLoader.Builder().setDomain(config.webDomain)
        config.webAssetPaths.forEach { path ->
            when (path.source) {
                MKWebAssetSource.assets -> {
                    builder.addPathHandler(
                        path.pathPrefix,
                        WebViewAssetLoader.AssetsPathHandler(context)
                    )
                }
                MKWebAssetSource.resources -> {
                    builder.addPathHandler(
                        path.pathPrefix,
                        WebViewAssetLoader.ResourcesPathHandler(context)
                    )
                }
            }
        }
        return builder.build()
    }

    private fun entryUrl(config: MKMapKitConfig): String = "https://${config.webDomain}${config.entryPath}"

    fun simulateAnnotationTap() {
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.simulateAnnotationTap && window.MKBridge.simulateAnnotationTap();")
    }

    fun simulateOverlayTap() {
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.simulateOverlayTap && window.MKBridge.simulateOverlayTap();")
    }

    fun simulatePan() {
        evaluateJavascriptSafe("window.MKBridge && window.MKBridge.simulatePan && window.MKBridge.simulatePan();")
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun serializeRegion(region: MKCoordinateRegion): String {
        return JSONObject()
            .put("centerLat", region.center.latitude)
            .put("centerLng", region.center.longitude)
            .put("latDelta", region.span.latitudeDelta)
            .put("lngDelta", region.span.longitudeDelta)
            .toString()
    }

    private fun serializeStateWithoutRegion(state: MKMapRenderState): String {

        val annotations = JSONArray().apply {
            state.annotations.forEach { annotation ->
                put(
                    JSONObject()
                        .put("id", annotation.id)
                        .put("lat", annotation.coordinate.latitude)
                        .put("lng", annotation.coordinate.longitude)
                        .put("title", annotation.title)
                        .put("subtitle", annotation.subtitle)
                        .put("isVisible", annotation.isVisible)
                        .put("isDraggable", annotation.isDraggable)
                        .put("style", serializeAnnotationStyle(annotation.style))
                )
            }
        }

        val overlays = JSONArray().apply {
            state.overlays.forEach { overlay ->
                val overlayJson = JSONObject()
                    .put("id", overlay.id)
                when (overlay) {
                    is MKPolylineOverlay -> {
                        overlayJson.put("type", "MKPolylineOverlay")
                        overlayJson.put(
                            "points",
                            JSONArray().apply {
                                overlay.points.forEach { point ->
                                    put(JSONObject().put("lat", point.latitude).put("lng", point.longitude))
                                }
                            }
                        )
                        overlayJson
                            .put("strokeColor", overlay.style.strokeColorHex)
                            .put("strokeWidth", overlay.style.strokeWidth)
                            .put("lineDashPattern", overlay.style.lineDashPattern?.let { JSONArray(it) })
                    }
                    is MKPolygonOverlay -> {
                        overlayJson.put("type", "MKPolygonOverlay")
                        overlayJson.put(
                            "points",
                            JSONArray().apply {
                                overlay.points.forEach { point ->
                                    put(JSONObject().put("lat", point.latitude).put("lng", point.longitude))
                                }
                            }
                        )
                        overlayJson
                            .put("strokeColor", overlay.style.strokeColorHex)
                            .put("strokeWidth", overlay.style.strokeWidth)
                            .put("fillColor", overlay.style.fillColorHex)
                            .put("lineDashPattern", overlay.style.lineDashPattern?.let { JSONArray(it) })
                    }
                    is MKCircleOverlay -> {
                        overlayJson.put("type", "MKCircleOverlay")
                        overlayJson
                            .put("centerLat", overlay.center.latitude)
                            .put("centerLng", overlay.center.longitude)
                            .put("radiusMeter", overlay.radiusMeter)
                            .put("strokeColor", overlay.style.strokeColorHex)
                            .put("strokeWidth", overlay.style.strokeWidth)
                            .put("fillColor", overlay.style.fillColorHex)
                            .put("lineDashPattern", overlay.style.lineDashPattern?.let { JSONArray(it) })
                    }
                    else -> Unit
                }
                put(overlayJson)
            }
        }

        return JSONObject()
            .put("annotations", annotations)
            .put("overlays", overlays)
            .put("mapStyle", state.options.mapStyle.name)
            .put("showsCompass", state.options.showsCompass)
            .put("showsScale", state.options.showsScale)
            .put("showsZoomControl", state.options.showsZoomControl)
            .put("showsMapTypeControl", state.options.showsMapTypeControl)
            .put("showsPointsOfInterest", state.options.showsPointsOfInterest)
            .put(
                "poiFilter",
                when (val filter = state.options.poiFilter) {
                    MKPoiFilter.All -> JSONObject().put("type", "MKPoiFilterAll")
                    MKPoiFilter.None -> JSONObject().put("type", "MKPoiFilterNone")
                    is MKPoiFilter.Include -> JSONObject()
                        .put("type", "MKPoiFilterInclude")
                        .put("categories", JSONArray(filter.categories))
                    is MKPoiFilter.Exclude -> JSONObject()
                        .put("type", "MKPoiFilterExclude")
                        .put("categories", JSONArray(filter.categories))
                }
            )
            .put("isRotateEnabled", state.options.isRotateEnabled)
            .put("isScrollEnabled", state.options.isScrollEnabled)
            .put("isZoomEnabled", state.options.isZoomEnabled)
            .put("isPitchEnabled", state.options.isPitchEnabled)
            .put("appearance", state.options.appearance.name)
            .put("language", state.options.language.name)
            .put("userLocationEnabled", state.options.userLocation.isEnabled)
            .put("userLocationFollowsHeading", state.options.userLocation.followsHeading)
            .put("userLocationShowsAccuracyRing", state.options.userLocation.showsAccuracyRing)
            .toString()
    }

    private fun parseEvent(json: JSONObject): MKMapEvent {
        return when (json.optString("type")) {
            "mapLoaded" -> MKMapEvent.MapLoaded
            "regionWillChange" -> {
                val region = json.getJSONObject("region")
                val internal = InternalMapState(
                    centerLat = region.getDouble("centerLat"),
                    centerLng = region.getDouble("centerLng"),
                    latDelta = region.getDouble("latDelta"),
                    lngDelta = region.getDouble("lngDelta")
                )
                MKMapEvent.RegionWillChange(MKBridgeMapper.toRegion(internal))
            }
            "regionDidChange" -> {
                val region = json.getJSONObject("region")
                val internal = InternalMapState(
                    centerLat = region.getDouble("centerLat"),
                    centerLng = region.getDouble("centerLng"),
                    latDelta = region.getDouble("latDelta"),
                    lngDelta = region.getDouble("lngDelta")
                )
                MKMapEvent.RegionDidChange(MKBridgeMapper.toRegion(internal))
            }
            "longPress" -> MKMapEvent.LongPress(
                coordinate = MKCoordinate(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng")
                )
            )
            "mapTapped" -> MKMapEvent.MapTapped(
                coordinate = MKCoordinate(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng")
                )
            )

            "annotationTapped" -> MKMapEvent.AnnotationTapped(id = json.getString("id"))
            "annotationSelected" -> MKMapEvent.AnnotationSelected(id = json.getString("id"))
            "annotationDeselected" -> MKMapEvent.AnnotationDeselected(id = json.getString("id"))
            "annotationDragStart" -> MKMapEvent.AnnotationDragStart(id = json.getString("id"))
            "annotationDragging" -> MKMapEvent.AnnotationDragging(
                id = json.getString("id"),
                coordinate = MKCoordinate(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng")
                )
            )
            "annotationDragEnd" -> MKMapEvent.AnnotationDragEnd(
                id = json.getString("id"),
                coordinate = MKCoordinate(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng")
                )
            )
            "overlayTapped" -> MKMapEvent.OverlayTapped(id = json.getString("id"))
            "bridgeError" -> MKMapEvent.MapError(
                MKMapErrorCause.BridgeFailure(json.optString("message", "bridge error"))
            )
            "debug" -> MKMapEvent.MapError(
                MKMapErrorCause.BridgeFailure(json.optString("message", "debug"))
            )
            "userLocationUpdated" -> MKMapEvent.UserLocationUpdated(
                coordinate = MKCoordinate(
                    latitude = json.getDouble("lat"),
                    longitude = json.getDouble("lng")
                )
            )

            else -> MKMapEvent.MapError(
                MKMapErrorCause.BridgeFailure("Unknown event type: ${json.optString("type")}")
            )
        }
    }

    private fun parseRegionResolution(json: JSONObject): Result<MKCoordinateRegion> {
        val ok = json.optBoolean("ok", false)
        if (!ok) {
            val type = json.optString("errorType", "bridgeFailure")
            val message = json.optString("message", "region resolution failed")
            return Result.failure(
                when (type) {
                    "timeout" -> MKRegionResolutionException.Timeout(
                        phase = json.optString("phase", "unknown"),
                        timeoutMs = json.optLong("timeoutMs", 0L)
                    )
                    "invalidSize" -> MKRegionResolutionException.InvalidSize(
                        widthPx = json.optInt("widthPx", 0),
                        heightPx = json.optInt("heightPx", 0)
                    )
                    else -> MKRegionResolutionException.BridgeFailure(message)
                }
            )
        }

        val region = json.getJSONObject("region")
        val internal = InternalMapState(
            centerLat = region.getDouble("centerLat"),
            centerLng = region.getDouble("centerLng"),
            latDelta = region.getDouble("latDelta"),
            lngDelta = region.getDouble("lngDelta")
        )
        return Result.success(MKBridgeMapper.toRegion(internal))
    }

    private fun serializeAnnotationStyle(style: MKAnnotationStyle): JSONObject {
        return when (style) {
            is MKAnnotationStyle.Marker -> JSONObject()
                .put("kind", "MKAnnotationStyleMarker")
                .put("tintHex", style.tintHex)
                .put("glyphText", style.glyphText)
                .put(
                    "glyphImageSource",
                    style.glyphImageSource?.let { serializeImageSource(it) }
                )

            is MKAnnotationStyle.Image -> JSONObject()
                .put("kind", "MKAnnotationStyleImage")
                .put("source", serializeImageSource(style.source))
                .put("widthDp", style.widthDp)
                .put("heightDp", style.heightDp)
                .put("anchorX", style.anchorX)
                .put("anchorY", style.anchorY)
        }
    }

    private fun serializeImageSource(source: MKImageSource): JSONObject {
        return when (source) {
            is MKImageSource.Url -> JSONObject()
                .put("kind", "MKImageSourceUrl")
                .put("value", source.value)

            is MKImageSource.Base64Png -> JSONObject()
                .put("kind", "MKImageSourceBase64Png")
                .put("value", source.value)

            is MKImageSource.ResourceName -> JSONObject()
                .put("kind", "MKImageSourceResourceName")
                .put("value", source.value)
        }
    }
}
