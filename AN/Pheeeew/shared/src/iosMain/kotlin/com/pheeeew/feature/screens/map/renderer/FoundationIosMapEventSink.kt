package com.pheeeew.feature.screens.map.renderer

interface FoundationIosMapEventSink {
    fun onCameraStateChanged(latitude: Double, longitude: Double, zoom: Double)

    fun onRendererUnavailable()

    fun onStyleLoadFailed()

    fun onMapRecovered()
}
