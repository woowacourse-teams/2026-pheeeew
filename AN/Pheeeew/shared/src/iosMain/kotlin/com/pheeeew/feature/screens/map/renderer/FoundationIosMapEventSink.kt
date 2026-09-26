package com.pheeeew.feature.screens.map.renderer

interface FoundationIosMapEventSink {
    fun onRendererUnavailable()

    fun onStyleLoadFailed()

    fun onMapRecovered()

    fun onRecordViewportChanged(
        centerX: Float,
        centerY: Float,
        radius: Float,
    )
}
