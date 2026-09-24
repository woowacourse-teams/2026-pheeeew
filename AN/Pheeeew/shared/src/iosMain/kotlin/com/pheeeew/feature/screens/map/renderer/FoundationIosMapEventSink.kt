package com.pheeeew.feature.screens.map.renderer

interface FoundationIosMapEventSink {
    fun onRendererUnavailable()

    fun onStyleLoadFailed()

    fun onMapRecovered()
}
