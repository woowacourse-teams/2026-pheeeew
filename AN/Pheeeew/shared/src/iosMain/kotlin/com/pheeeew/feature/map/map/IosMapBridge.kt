package com.pheeeew.feature.map.map

import com.pheeeew.feature.map.star.StarVisual
import platform.UIKit.UIView

/** Swift MapLibre renderer가 Kotlin 상태를 소비하기 위한 최소 브리지 계약입니다. */
interface IosNativeMapFactory {
    fun createMapView(eventSink: IosMapEventSink): UIView

    fun updateMapView(
        mapView: UIView,
        state: IosMapRenderState,
    )

    fun releaseMapView(mapView: UIView)
}

interface IosMapEventSink {
    fun onSighClick(id: String)

    fun onBoundsChanged(
        minLongitude: Double,
        minLatitude: Double,
        maxLongitude: Double,
        maxLatitude: Double,
    )

    fun onRendererUnavailable()

    fun onStyleLoadFailed()

    fun onMapRecovered()

    fun onProjectionChanged(
        points: List<IosMapScreenPoint>,
        cameraIdle: Boolean,
    )
}

data class IosMapCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    override fun toString(): String = "IosMapCoordinate([redacted])"
}

data class IosSighMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val visual: StarVisual,
) {
    override fun toString(): String = "IosSighMarker(id=$id, coordinate=[redacted])"
}

data class IosCurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
) {
    override fun toString(): String = "IosCurrentLocation([redacted])"
}

data class IosMapFocusRequest(
    val id: String,
    val latitude: Double,
    val longitude: Double,
) {
    override fun toString(): String = "IosMapFocusRequest(id=$id, coordinate=[redacted])"
}

data class IosMapScreenPoint(
    val id: String,
    val xPx: Double,
    val yPx: Double,
)

enum class IosMapCameraCommandKind {
    ZoomBy,
    MoveToCurrentLocation,
}

data class IosMapCameraCommand(
    val id: Long,
    val kind: IosMapCameraCommandKind,
    val delta: Double,
    val zoom: Double?,
)

data class IosMapRenderState(
    val sighMarkers: List<IosSighMarker>,
    val currentLocation: IosCurrentLocation?,
    val initialCenter: IosMapCoordinate?,
    val initialCenterIsProvisional: Boolean,
    val focusRequest: IosMapFocusRequest?,
    val cameraCommand: IosMapCameraCommand?,
) {
    override fun toString(): String = "IosMapRenderState([redacted])"
}

/**
 * Swift가 앱 시작 시 factory를 등록하면 Compose의 [NativeBreathMap]이
 * 해당 factory가 만든 MLNMapView를 네이티브 interop 영역에 배치합니다.
 */
object IosMapBridge {
    private var factory: IosNativeMapFactory? = null

    fun registerFactory(factory: IosNativeMapFactory) {
        this.factory = factory
    }

    internal fun createMapView(eventSink: IosMapEventSink): UIView =
        factory?.createMapView(eventSink) ?: UIView().also {
            eventSink.onRendererUnavailable()
        }

    internal fun updateMapView(
        mapView: UIView,
        state: IosMapRenderState,
    ) {
        factory?.updateMapView(mapView, state)
    }

    internal fun releaseMapView(mapView: UIView) {
        factory?.releaseMapView(mapView)
    }
}
