package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionRegionLevel

internal object MapZoomPolicy {
    private const val SIGUNGU_MIN_ZOOM = 9.0
    const val DETAIL_PIN_MIN_ZOOM = 12.0

    fun regionLevelForZoom(zoom: Double): EmotionRegionLevel? =
        when {
            zoom < SIGUNGU_MIN_ZOOM -> EmotionRegionLevel.SIDO
            zoom < DETAIL_PIN_MIN_ZOOM -> EmotionRegionLevel.SIGUNGU
            else -> null
        }

    fun focusZoomForRegion(level: EmotionRegionLevel): Double =
        when (level) {
            EmotionRegionLevel.SIDO -> SIGUNGU_MIN_ZOOM
            EmotionRegionLevel.SIGUNGU -> DETAIL_PIN_MIN_ZOOM
        }
}
