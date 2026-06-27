package com.studiomk.mapkit.model

data class MKCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class MKCoordinateSpan(
    val latitudeDelta: Double,
    val longitudeDelta: Double
)

data class MKEdgePadding(
    val top: Double,
    val left: Double,
    val bottom: Double,
    val right: Double
)

data class MKCoordinateRegion(
    val center: MKCoordinate,
    val span: MKCoordinateSpan
) {
    companion object {
        fun fromCenter(
            center: MKCoordinate,
            latitudeDelta: Double,
            longitudeDelta: Double
        ): MKCoordinateRegion = MKCoordinateRegion(
            center = center,
            span = MKCoordinateSpan(latitudeDelta, longitudeDelta)
        )

        fun fromCoordinates(
            coordinates: List<MKCoordinate>,
            edgePadding: MKEdgePadding = MKEdgePadding(0.0, 0.0, 0.0, 0.0)
        ): MKCoordinateRegion {
            require(coordinates.isNotEmpty()) { "coordinates must not be empty" }

            val minLat = coordinates.minOf { it.latitude }
            val maxLat = coordinates.maxOf { it.latitude }
            val minLng = coordinates.minOf { it.longitude }
            val maxLng = coordinates.maxOf { it.longitude }

            val center = MKCoordinate(
                latitude = (minLat + maxLat) / 2.0,
                longitude = (minLng + maxLng) / 2.0
            )
            return fromCenter(
                center = center,
                latitudeDelta = (maxLat - minLat) + edgePadding.top + edgePadding.bottom,
                longitudeDelta = (maxLng - minLng) + edgePadding.left + edgePadding.right
            )
        }
    }
}

sealed interface MKImageSource {
    data class Url(val value: String) : MKImageSource
    data class Base64Png(val value: String) : MKImageSource
    data class ResourceName(val value: String) : MKImageSource
}

sealed interface MKAnnotationStyle {
    data class Marker(
        val tintHex: String = "#FF3B30",
        val glyphText: String? = null,
        val glyphImageSource: MKImageSource? = MKImageSource.ResourceName("pin")
    ) : MKAnnotationStyle

    data class Image(
        val source: MKImageSource,
        val widthDp: Int,
        val heightDp: Int,
        val anchorX: Double = 0.5,
        val anchorY: Double = 1.0
    ) : MKAnnotationStyle
}

data class MKAnnotation(
    val id: String,
    val coordinate: MKCoordinate,
    val title: String? = null,
    val subtitle: String? = null,
    val isVisible: Boolean = true,
    val isDraggable: Boolean = false,
    val style: MKAnnotationStyle = MKAnnotationStyle.Marker()
)

data class MKOverlayStyle(
    val strokeColorHex: String = "#007AFF",
    val strokeWidth: Double = 2.0,
    val fillColorHex: String? = null,
    val lineDashPattern: List<Double>? = null
)

sealed interface MKOverlay {
    val id: String
    val isVisible: Boolean
    val zIndex: Int
    val style: MKOverlayStyle
}

data class MKPolylineOverlay(
    override val id: String,
    val points: List<MKCoordinate>,
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

data class MKPolygonOverlay(
    override val id: String,
    val points: List<MKCoordinate>,
    val holes: List<List<MKCoordinate>> = emptyList(),
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

data class MKCircleOverlay(
    override val id: String,
    val center: MKCoordinate,
    val radiusMeter: Double,
    override val style: MKOverlayStyle = MKOverlayStyle(),
    override val isVisible: Boolean = true,
    override val zIndex: Int = 0
) : MKOverlay

enum class MKMapStyle {
    standard,
    mutedStandard,
    satellite,
    hybrid
}

enum class MKAppearanceOption {
    auto,
    light,
    dark
}

enum class MKMapLanguage {
    auto,
    ja,
    en
}

typealias MKPointOfInterestCategoryValue = String

@Suppress("MemberVisibilityCanBePrivate")
object MKPointOfInterestCategoryValues {
    const val ATM: MKPointOfInterestCategoryValue = "ATM"
    const val Airport: MKPointOfInterestCategoryValue = "Airport"
    const val AmusementPark: MKPointOfInterestCategoryValue = "AmusementPark"
    const val AnimalService: MKPointOfInterestCategoryValue = "AnimalService"
    const val Aquarium: MKPointOfInterestCategoryValue = "Aquarium"
    const val AutomotiveRepair: MKPointOfInterestCategoryValue = "AutomotiveRepair"
    const val Bakery: MKPointOfInterestCategoryValue = "Bakery"
    const val Bank: MKPointOfInterestCategoryValue = "Bank"
    const val Baseball: MKPointOfInterestCategoryValue = "Baseball"
    const val Basketball: MKPointOfInterestCategoryValue = "Basketball"
    const val Beach: MKPointOfInterestCategoryValue = "Beach"
    const val Beauty: MKPointOfInterestCategoryValue = "Beauty"
    const val Bowling: MKPointOfInterestCategoryValue = "Bowling"
    const val Brewery: MKPointOfInterestCategoryValue = "Brewery"
    const val Cafe: MKPointOfInterestCategoryValue = "Cafe"
    const val Campground: MKPointOfInterestCategoryValue = "Campground"
    const val CarRental: MKPointOfInterestCategoryValue = "CarRental"
    const val Castle: MKPointOfInterestCategoryValue = "Castle"
    const val ConventionCenter: MKPointOfInterestCategoryValue = "ConventionCenter"
    const val Distillery: MKPointOfInterestCategoryValue = "Distillery"
    const val EVCharger: MKPointOfInterestCategoryValue = "EVCharger"
    const val Fairground: MKPointOfInterestCategoryValue = "Fairground"
    const val FireStation: MKPointOfInterestCategoryValue = "FireStation"
    const val Fishing: MKPointOfInterestCategoryValue = "Fishing"
    const val FitnessCenter: MKPointOfInterestCategoryValue = "FitnessCenter"
    const val FoodMarket: MKPointOfInterestCategoryValue = "FoodMarket"
    const val Fortress: MKPointOfInterestCategoryValue = "Fortress"
    const val GasStation: MKPointOfInterestCategoryValue = "GasStation"
    const val GoKart: MKPointOfInterestCategoryValue = "GoKart"
    const val Golf: MKPointOfInterestCategoryValue = "Golf"
    const val Hiking: MKPointOfInterestCategoryValue = "Hiking"
    const val Hospital: MKPointOfInterestCategoryValue = "Hospital"
    const val Hotel: MKPointOfInterestCategoryValue = "Hotel"
    const val Kayaking: MKPointOfInterestCategoryValue = "Kayaking"
    const val Landmark: MKPointOfInterestCategoryValue = "Landmark"
    const val Laundry: MKPointOfInterestCategoryValue = "Laundry"
    const val Library: MKPointOfInterestCategoryValue = "Library"
    const val Mailbox: MKPointOfInterestCategoryValue = "Mailbox"
    const val Marina: MKPointOfInterestCategoryValue = "Marina"
    const val MiniGolf: MKPointOfInterestCategoryValue = "MiniGolf"
    const val MovieTheater: MKPointOfInterestCategoryValue = "MovieTheater"
    const val Museum: MKPointOfInterestCategoryValue = "Museum"
    const val MusicVenue: MKPointOfInterestCategoryValue = "MusicVenue"
    const val NationalMonument: MKPointOfInterestCategoryValue = "NationalMonument"
    const val NationalPark: MKPointOfInterestCategoryValue = "NationalPark"
    const val Nightlife: MKPointOfInterestCategoryValue = "Nightlife"
    const val Park: MKPointOfInterestCategoryValue = "Park"
    const val Parking: MKPointOfInterestCategoryValue = "Parking"
    const val Pharmacy: MKPointOfInterestCategoryValue = "Pharmacy"
    const val Planetarium: MKPointOfInterestCategoryValue = "Planetarium"
    const val Police: MKPointOfInterestCategoryValue = "Police"
    const val PostOffice: MKPointOfInterestCategoryValue = "PostOffice"
    const val PublicTransport: MKPointOfInterestCategoryValue = "PublicTransport"
    const val RVPark: MKPointOfInterestCategoryValue = "RVPark"
    const val Restaurant: MKPointOfInterestCategoryValue = "Restaurant"
    const val Restroom: MKPointOfInterestCategoryValue = "Restroom"
    const val RockClimbing: MKPointOfInterestCategoryValue = "RockClimbing"
    const val School: MKPointOfInterestCategoryValue = "School"
    const val SkatePark: MKPointOfInterestCategoryValue = "SkatePark"
    const val Skating: MKPointOfInterestCategoryValue = "Skating"
    const val Skiing: MKPointOfInterestCategoryValue = "Skiing"
    const val Soccer: MKPointOfInterestCategoryValue = "Soccer"
    const val Spa: MKPointOfInterestCategoryValue = "Spa"
    const val Stadium: MKPointOfInterestCategoryValue = "Stadium"
    const val Store: MKPointOfInterestCategoryValue = "Store"
    const val Surfing: MKPointOfInterestCategoryValue = "Surfing"
    const val Swimming: MKPointOfInterestCategoryValue = "Swimming"
    const val Tennis: MKPointOfInterestCategoryValue = "Tennis"
    const val Theater: MKPointOfInterestCategoryValue = "Theater"
    const val University: MKPointOfInterestCategoryValue = "University"
    const val Volleyball: MKPointOfInterestCategoryValue = "Volleyball"
    const val Winery: MKPointOfInterestCategoryValue = "Winery"
    const val Zoo: MKPointOfInterestCategoryValue = "Zoo"
}

sealed interface MKPoiFilter {
    data object All : MKPoiFilter
    data object None : MKPoiFilter
    data class Include(val categories: List<MKPointOfInterestCategoryValue>) : MKPoiFilter
    data class Exclude(val categories: List<MKPointOfInterestCategoryValue>) : MKPoiFilter
}

data class MKUserLocationOptions(
    val isEnabled: Boolean = false,
    val followsHeading: Boolean = false,
    val showsAccuracyRing: Boolean = true
)

data class MKMapOptions(
    val mapStyle: MKMapStyle = MKMapStyle.standard,
    val showsCompass: Boolean = true,
    val showsScale: Boolean = false,
    val showsZoomControl: Boolean = false,
    val showsMapTypeControl: Boolean = false,
    val showsPointsOfInterest: Boolean = true,
    val poiFilter: MKPoiFilter = MKPoiFilter.All,
    val isRotateEnabled: Boolean = true,
    val isScrollEnabled: Boolean = true,
    val isZoomEnabled: Boolean = true,
    val isPitchEnabled: Boolean = true,
    val appearance: MKAppearanceOption = MKAppearanceOption.auto,
    val language: MKMapLanguage = MKMapLanguage.auto,
    val userLocation: MKUserLocationOptions = MKUserLocationOptions()
)

enum class MKWebAssetSource {
    assets,
    resources
}

data class MKWebAssetPath(
    val source: MKWebAssetSource,
    val pathPrefix: String
) {
    init {
        require(pathPrefix.startsWith("/")) { "pathPrefix must start with '/': $pathPrefix" }
        require(pathPrefix.endsWith("/")) { "pathPrefix must end with '/': $pathPrefix" }
    }

    companion object {
        fun assets(pathPrefix: String): MKWebAssetPath = MKWebAssetPath(
            source = MKWebAssetSource.assets,
            pathPrefix = pathPrefix
        )

        fun resources(pathPrefix: String): MKWebAssetPath = MKWebAssetPath(
            source = MKWebAssetSource.resources,
            pathPrefix = pathPrefix
        )
    }
}

data class MKMapKitConfig(
    val webDomain: String = "appassets.androidplatform.net",
    val webAssetPaths: List<MKWebAssetPath> = listOf(MKWebAssetPath.assets("/assets/")),
    val entryPath: String = "/assets/mkbridge/index.html"
) {
    init {
        require(webDomain.isNotBlank()) { "webDomain must not be blank" }
        require(webAssetPaths.isNotEmpty()) { "webAssetPaths must not be empty" }
        require(entryPath.startsWith("/")) { "entryPath must start with '/': $entryPath" }
    }
}

data class MKRegionAdjustmentRequest(
    val region: MKCoordinateRegion,
    val widthPx: Int,
    val heightPx: Int
) {
    init {
        require(widthPx > 0) { "widthPx must be > 0: $widthPx" }
        require(heightPx > 0) { "heightPx must be > 0: $heightPx" }
    }
}

interface MKRegionAdjuster {
    suspend fun adjust(request: MKRegionAdjustmentRequest): MKCoordinateRegion
}

sealed class MKRegionResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotInitialized : MKRegionResolutionException("MKMapKit is not initialized")
    data class InvalidSize(val widthPx: Int, val heightPx: Int) : MKRegionResolutionException(
        "Requested size must be positive: ${widthPx}x$heightPx"
    )
    data class Timeout(val phase: String, val timeoutMs: Long) : MKRegionResolutionException(
        "Timed out while waiting for $phase (${timeoutMs}ms)"
    )
    data class BridgeFailure(val detail: String, val source: Throwable? = null) : MKRegionResolutionException(
        detail,
        source
    )
}

data class MKMapRenderState(
    val region: MKCoordinateRegion,
    val annotations: List<MKAnnotation> = emptyList(),
    val overlays: List<MKOverlay> = emptyList(),
    val options: MKMapOptions = MKMapOptions()
)

sealed interface MKMapCommand {
    data class SelectAnnotation(val annotationId: String, val animated: Boolean = true) : MKMapCommand
    data class DeselectAnnotation(val animated: Boolean = false) : MKMapCommand
}

class MKMapController {
    private val pendingCommands = ArrayDeque<MKMapCommand>()
    private var dispatcher: ((MKMapCommand) -> Unit)? = null

    @Synchronized
    fun selectAnnotation(id: String, animated: Boolean = true) {
        dispatch(MKMapCommand.SelectAnnotation(id, animated))
    }

    @Synchronized
    fun deselectAnnotation(animated: Boolean = false) {
        dispatch(MKMapCommand.DeselectAnnotation(animated))
    }

    @Synchronized
    fun bindCommandDispatcher(nextDispatcher: (MKMapCommand) -> Unit) {
        dispatcher = nextDispatcher
        while (pendingCommands.isNotEmpty()) {
            nextDispatcher(pendingCommands.removeFirst())
        }
    }

    @Synchronized
    fun clearCommandDispatcher() {
        dispatcher = null
    }

    @Synchronized
    private fun dispatch(command: MKMapCommand) {
        val current = dispatcher
        if (current != null) {
            current(command)
        } else {
            pendingCommands.addLast(command)
        }
    }
}

sealed interface MKMapErrorCause {
    data object NotInitialized : MKMapErrorCause
    data object TokenUnavailable : MKMapErrorCause
    data class BridgeFailure(val message: String) : MKMapErrorCause
}

sealed interface MKMapEvent {
    data object MapLoaded : MKMapEvent
    data class MapError(val cause: MKMapErrorCause) : MKMapEvent
    data class RegionWillChange(val region: MKCoordinateRegion) : MKMapEvent
    data class RegionDidChange(val region: MKCoordinateRegion) : MKMapEvent
    data class MapTapped(val coordinate: MKCoordinate) : MKMapEvent
    data class LongPress(val coordinate: MKCoordinate) : MKMapEvent
    data class AnnotationTapped(val id: String) : MKMapEvent
    data class AnnotationSelected(val id: String) : MKMapEvent
    data class AnnotationDeselected(val id: String) : MKMapEvent
    data class AnnotationDragStart(val id: String) : MKMapEvent
    data class AnnotationDragging(val id: String, val coordinate: MKCoordinate) : MKMapEvent
    data class AnnotationDragEnd(val id: String, val coordinate: MKCoordinate) : MKMapEvent
    data class OverlayTapped(val id: String) : MKMapEvent
    data class UserLocationUpdated(val coordinate: MKCoordinate) : MKMapEvent
}
