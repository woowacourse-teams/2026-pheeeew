package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel

data class RegionClusterUiModel(
    val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val count: Long,
    val representativeEmotion: EmotionTypeUiModel?,
) {
    fun symbolImageKey(): String = "region-$name-$count-${representativeEmotion?.name ?: "none"}"
}

internal fun EmotionState.toRegionUiEmotion(): EmotionTypeUiModel =
    when (this) {
        EmotionState.FRUSTRATED -> EmotionTypeUiModel.FRUSTRATED
        EmotionState.IRRITATED -> EmotionTypeUiModel.IRRITATED
        EmotionState.EXHAUSTED -> EmotionTypeUiModel.EXHAUSTED
        EmotionState.DISCOURAGED -> EmotionTypeUiModel.DISCOURAGED
        EmotionState.ANGRY -> EmotionTypeUiModel.ANGRY
    }
