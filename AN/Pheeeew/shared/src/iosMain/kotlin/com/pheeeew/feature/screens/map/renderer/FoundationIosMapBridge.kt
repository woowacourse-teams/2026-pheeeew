package com.pheeeew.feature.screens.map.renderer

import platform.UIKit.UIView

object FoundationIosMapBridge {
    private var factory: FoundationIosMapFactory? = null

    fun registerFactory(factory: FoundationIosMapFactory) {
        this.factory = factory
    }

    internal fun createMapView(eventSink: FoundationIosMapEventSink): UIView =
        factory?.createMapView(eventSink) ?: UIView().also { eventSink.onRendererUnavailable() }

    internal fun updateMapView(mapView: UIView, state: FoundationIosMapRenderUiModel) {
        factory?.updateMapView(mapView, state)
    }

    internal fun releaseMapView(mapView: UIView) {
        factory?.releaseMapView(mapView)
    }
}
