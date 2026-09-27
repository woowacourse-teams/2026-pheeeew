package com.pheeeew.feature.screens.map.renderer

interface FoundationIosMapEventSink {
    fun onViewportChanged(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
    )

    fun onRendererUnavailable()

    fun onStyleLoadFailed()

    fun onMapRecovered()

    fun onRecordViewportChanged(
        centerX: Float,
        centerY: Float,
        radius: Float,
    )
}
