package com.studiomk.mapkit.api

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.studiomk.mapkit.model.MKAnnotation
import com.studiomk.mapkit.model.MKAnnotationStyle
import com.studiomk.mapkit.model.MKCircleOverlay
import com.studiomk.mapkit.model.MKCoordinate
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKMapErrorCause
import com.studiomk.mapkit.model.MKMapEvent
import com.studiomk.mapkit.model.MKMapOptions
import com.studiomk.mapkit.model.MKMapRenderState
import com.studiomk.mapkit.model.MKMapController
import com.studiomk.mapkit.model.MKOverlay
import com.studiomk.mapkit.model.MKOverlayStyle
import com.studiomk.mapkit.model.MKPolygonOverlay
import com.studiomk.mapkit.model.MKPolylineOverlay
import com.studiomk.mapkit.model.MKAnnotation as MKAnnotationModel
import com.studiomk.mapkit.model.MKCircleOverlay as MKCircleOverlayModel
import com.studiomk.mapkit.model.MKPolygonOverlay as MKPolygonOverlayModel
import com.studiomk.mapkit.model.MKPolylineOverlay as MKPolylineOverlayModel
import com.studiomk.mapkit.webview.MKBridgeWebView

@DslMarker
annotation class MKMapDsl

@Stable
@Composable
fun rememberMKMapController(): MKMapController = remember { MKMapController() }

internal data class MKAnnotationCallbacks(
    val onSelected: (() -> Unit)? = null,
    val onDeselected: (() -> Unit)? = null,
    val onDragStart: (() -> Unit)? = null,
    val onDrag: ((MKCoordinate) -> Unit)? = null,
    val onDragEnd: ((MKCoordinate) -> Unit)? = null
)

internal data class MKOverlayCallbacks(
    val onTap: (() -> Unit)? = null
)

internal data class MKCollectedContent(
    val annotations: List<MKAnnotation>,
    val overlays: List<MKOverlay>,
    val annotationCallbacksById: Map<String, MKAnnotationCallbacks>,
    val overlayCallbacksById: Map<String, MKOverlayCallbacks>
)

@MKMapDsl
interface MKMapContentScope {
    fun MKAnnotation(
        id: String,
        coordinate: MKCoordinate,
        title: String? = null,
        subtitle: String? = null,
        isVisible: Boolean = true,
        isDraggable: Boolean = false,
        style: MKAnnotationStyle = MKAnnotationStyle.Marker(),
        onSelected: (() -> Unit)? = null,
        onDeselected: (() -> Unit)? = null,
        onDragStart: (() -> Unit)? = null,
        onDrag: ((MKCoordinate) -> Unit)? = null,
        onDragEnd: ((MKCoordinate) -> Unit)? = null
    )

    fun MKPolylineOverlay(
        id: String,
        points: List<MKCoordinate>,
        style: MKOverlayStyle = MKOverlayStyle(),
        isVisible: Boolean = true,
        zIndex: Int = 0,
        onTap: (() -> Unit)? = null
    )

    fun MKPolygonOverlay(
        id: String,
        points: List<MKCoordinate>,
        holes: List<List<MKCoordinate>> = emptyList(),
        style: MKOverlayStyle = MKOverlayStyle(),
        isVisible: Boolean = true,
        zIndex: Int = 0,
        onTap: (() -> Unit)? = null
    )

    fun MKCircleOverlay(
        id: String,
        center: MKCoordinate,
        radiusMeter: Double,
        style: MKOverlayStyle = MKOverlayStyle(),
        isVisible: Boolean = true,
        zIndex: Int = 0,
        onTap: (() -> Unit)? = null
    )
}

private class MKMapContentCollector : MKMapContentScope {
    private val annotations = mutableListOf<MKAnnotation>()
    private val overlays = mutableListOf<MKOverlay>()
    private val annotationCallbacksById = linkedMapOf<String, MKAnnotationCallbacks>()
    private val overlayCallbacksById = linkedMapOf<String, MKOverlayCallbacks>()

    override fun MKAnnotation(
        id: String,
        coordinate: MKCoordinate,
        title: String?,
        subtitle: String?,
        isVisible: Boolean,
        isDraggable: Boolean,
        style: MKAnnotationStyle,
        onSelected: (() -> Unit)?,
        onDeselected: (() -> Unit)?,
        onDragStart: (() -> Unit)?,
        onDrag: ((MKCoordinate) -> Unit)?,
        onDragEnd: ((MKCoordinate) -> Unit)?
    ) {
        registerUniqueId(id)
        annotations += MKAnnotationModel(
            id = id,
            coordinate = coordinate,
            title = title,
            subtitle = subtitle,
            isVisible = isVisible,
            isDraggable = isDraggable,
            style = style
        )
        annotationCallbacksById[id] = MKAnnotationCallbacks(
            onSelected = onSelected,
            onDeselected = onDeselected,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd
        )
    }

    override fun MKPolylineOverlay(
        id: String,
        points: List<MKCoordinate>,
        style: MKOverlayStyle,
        isVisible: Boolean,
        zIndex: Int,
        onTap: (() -> Unit)?
    ) {
        registerUniqueId(id)
        overlays += MKPolylineOverlayModel(
            id = id,
            points = points,
            style = style,
            isVisible = isVisible,
            zIndex = zIndex
        )
        overlayCallbacksById[id] = MKOverlayCallbacks(onTap = onTap)
    }

    override fun MKPolygonOverlay(
        id: String,
        points: List<MKCoordinate>,
        holes: List<List<MKCoordinate>>,
        style: MKOverlayStyle,
        isVisible: Boolean,
        zIndex: Int,
        onTap: (() -> Unit)?
    ) {
        registerUniqueId(id)
        overlays += MKPolygonOverlayModel(
            id = id,
            points = points,
            holes = holes,
            style = style,
            isVisible = isVisible,
            zIndex = zIndex
        )
        overlayCallbacksById[id] = MKOverlayCallbacks(onTap = onTap)
    }

    override fun MKCircleOverlay(
        id: String,
        center: MKCoordinate,
        radiusMeter: Double,
        style: MKOverlayStyle,
        isVisible: Boolean,
        zIndex: Int,
        onTap: (() -> Unit)?
    ) {
        registerUniqueId(id)
        overlays += MKCircleOverlayModel(
            id = id,
            center = center,
            radiusMeter = radiusMeter,
            style = style,
            isVisible = isVisible,
            zIndex = zIndex
        )
        overlayCallbacksById[id] = MKOverlayCallbacks(onTap = onTap)
    }

    fun collect(block: MKMapContentScope.() -> Unit): MKCollectedContent {
        block(this)
        return MKCollectedContent(
            annotations = annotations.toList(),
            overlays = overlays.toList(),
            annotationCallbacksById = annotationCallbacksById.toMap(),
            overlayCallbacksById = overlayCallbacksById.toMap()
        )
    }

    private fun registerUniqueId(id: String) {
        require(id.isNotBlank()) { "id must not be blank" }
        require(id !in annotationCallbacksById && id !in overlayCallbacksById) {
            "Duplicated map content id: $id"
        }
    }
}

private fun MKBridgeWebView.installParentScrollInterceptionGuard() {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_POINTER_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        false
    }
}

@Composable
fun MKMapView(
    region: MKCoordinateRegion,
    controller: MKMapController,
    options: MKMapOptions = MKMapOptions(),
    modifier: Modifier = Modifier,
    session: MKMapSession? = null,
    onRegionWillChange: ((MKCoordinateRegion) -> Unit)? = null,
    onRegionDidChange: ((MKCoordinateRegion) -> Unit)? = null,
    onMapTapped: ((MKCoordinate) -> Unit)? = null,
    onLongPress: ((MKCoordinate) -> Unit)? = null,
    onMapLoaded: (() -> Unit)? = null,
    onMapError: ((MKMapErrorCause) -> Unit)? = null,
    onUserLocationUpdated: ((MKCoordinate) -> Unit)? = null,
    content: MKMapContentScope.() -> Unit
) {
    val sessionOwner = remember { Any() }
    DisposableEffect(session, sessionOwner) {
        onDispose { session?.release(sessionOwner) }
    }
    val collected = MKMapContentCollector().collect(content)
    val renderState = MKMapRenderState(
        region = region,
        annotations = collected.annotations,
        overlays = collected.overlays,
        options = options
    )

    val latestAnnotationCallbacks = rememberUpdatedState(collected.annotationCallbacksById)
    val latestOverlayCallbacks = rememberUpdatedState(collected.overlayCallbacksById)
    val latestOnRegionWillChange = rememberUpdatedState(onRegionWillChange)
    val latestOnRegionDidChange = rememberUpdatedState(onRegionDidChange)
    val latestOnMapTapped = rememberUpdatedState(onMapTapped)
    val latestOnLongPress = rememberUpdatedState(onLongPress)
    val latestOnMapLoaded = rememberUpdatedState(onMapLoaded)
    val latestOnMapError = rememberUpdatedState(onMapError)
    val latestOnUserLocationUpdated = rememberUpdatedState(onUserLocationUpdated)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val config = MKMapKit.currentConfig()
            (session?.acquire(sessionOwner) ?: MKBridgeWebView(context, mapKitConfig = config)).also { webView ->
                webView.installParentScrollInterceptionGuard()
            }
        },
        update = { webView ->
            if (session == null || session.owns(sessionOwner)) {
                webView.applyMapKitConfig(MKMapKit.currentConfig())
                webView.setEventListener { event ->
                    when (event) {
                        is MKMapEvent.MapLoaded -> latestOnMapLoaded.value?.invoke()
                        is MKMapEvent.MapError -> latestOnMapError.value?.invoke(event.cause)
                        is MKMapEvent.RegionWillChange -> latestOnRegionWillChange.value?.invoke(event.region)
                        is MKMapEvent.RegionDidChange -> latestOnRegionDidChange.value?.invoke(event.region)
                        is MKMapEvent.MapTapped -> latestOnMapTapped.value?.invoke(event.coordinate)
                        is MKMapEvent.LongPress -> latestOnLongPress.value?.invoke(event.coordinate)
                        is MKMapEvent.UserLocationUpdated -> latestOnUserLocationUpdated.value?.invoke(event.coordinate)

                        is MKMapEvent.AnnotationSelected -> {
                            latestAnnotationCallbacks.value[event.id]?.onSelected?.invoke()
                        }
                        is MKMapEvent.AnnotationDeselected -> {
                            latestAnnotationCallbacks.value[event.id]?.onDeselected?.invoke()
                        }
                        is MKMapEvent.AnnotationDragStart -> {
                            latestAnnotationCallbacks.value[event.id]?.onDragStart?.invoke()
                        }
                        is MKMapEvent.AnnotationDragging -> {
                            latestAnnotationCallbacks.value[event.id]?.onDrag?.invoke(event.coordinate)
                        }
                        is MKMapEvent.AnnotationDragEnd -> {
                            latestAnnotationCallbacks.value[event.id]?.onDragEnd?.invoke(event.coordinate)
                        }
                        is MKMapEvent.OverlayTapped -> {
                            latestOverlayCallbacks.value[event.id]?.onTap?.invoke()
                        }
                        else -> Unit
                    }
                }
                if (session != null) {
                    session.bind(sessionOwner, controller)
                } else {
                    controller.bindCommandDispatcher { command -> webView.applyCommand(command) }
                }
                val token = MKMapKit.currentTokenOrNull()
                if (token != null) {
                    webView.ensureInitialized(token)
                    webView.applyState(renderState)
                } else {
                    latestOnMapError.value?.invoke(MKMapErrorCause.TokenUnavailable)
                }
            }
        }
    )
}
