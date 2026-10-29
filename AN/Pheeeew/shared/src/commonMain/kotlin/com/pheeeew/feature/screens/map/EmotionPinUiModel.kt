package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel

data class EmotionPinUiModel(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val createdAt: String,
    val rotationDegrees: Double,
    val emotion: EmotionTypeUiModel,
)

fun EmotionMapPin.toUiModel() =
    EmotionPinUiModel(
        id = id,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt,
        rotationDegrees = rotationDegrees,
        emotion = state.toUiEmotion(),
    )

private fun EmotionState.toUiEmotion(): EmotionTypeUiModel =
    when (this) {
        EmotionState.FRUSTRATED -> EmotionTypeUiModel.FRUSTRATED
        EmotionState.IRRITATED -> EmotionTypeUiModel.IRRITATED
        EmotionState.EXHAUSTED -> EmotionTypeUiModel.EXHAUSTED
        EmotionState.DISCOURAGED -> EmotionTypeUiModel.DISCOURAGED
        EmotionState.ANGRY -> EmotionTypeUiModel.ANGRY
    }
