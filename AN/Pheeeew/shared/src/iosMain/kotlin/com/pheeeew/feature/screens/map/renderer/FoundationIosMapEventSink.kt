package com.pheeeew.feature.screens.map.renderer

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.feature.screens.map.HighlightedPinPosition

interface FoundationIosMapEventSink {
    fun onHighlightedPinPositionChanged(position: HighlightedPinPosition?)

    fun onContentPresented(
        loadId: String,
        entryIds: List<String>,
    )

    fun onEmotionPinClick(id: Long)

    fun onMapBackgroundClick()

    fun onRendererUnavailable()

    fun onStyleLoadFailed()

    fun onMapRecovered()

    fun onRecordViewportChanged(
        centerX: Float,
        centerY: Float,
        radius: Float,
    )

    fun onViewportChanged(bounds: EmotionMapBounds)
}
