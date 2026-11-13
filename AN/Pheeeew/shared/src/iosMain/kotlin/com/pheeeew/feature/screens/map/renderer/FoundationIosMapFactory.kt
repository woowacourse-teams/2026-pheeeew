package com.pheeeew.feature.screens.map.renderer

import platform.UIKit.UIView

interface FoundationIosMapFactory {
    fun createMapView(
        eventSink: FoundationIosMapEventSink,
        styleUrlOverride: String?,
    ): UIView

    fun updateMapView(
        mapView: UIView,
        state: FoundationIosMapRenderUiModel,
    )

    fun releaseMapView(mapView: UIView)
}
