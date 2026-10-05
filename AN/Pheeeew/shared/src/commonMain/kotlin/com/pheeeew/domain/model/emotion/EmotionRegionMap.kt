package com.pheeeew.domain.model.emotion

enum class EmotionRegionLevel { SIDO, SIGUNGU, EMD }

data class EmotionMapViewport(
    val bounds: EmotionMapBounds,
    val zoom: Double,
) {
    fun isValid(): Boolean = bounds.isValid() && zoom.isFinite() && zoom >= 0.0
}

data class EmotionRegion(
    val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val count: Long,
    val representativeState: EmotionState?,
)

sealed interface EmotionRegionResult {
    data class Success(val regions: List<EmotionRegion>) : EmotionRegionResult
    data object Failure : EmotionRegionResult
}
