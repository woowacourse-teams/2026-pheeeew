package com.pheeeew.domain.model.emotion

import com.pheeeew.domain.model.GeoCoordinate

data class EmotionRegistration(
    val requestId: String,
    val state: EmotionState,
    val coordinate: GeoCoordinate,
    val rotationDegrees: Double,
    val groupId: String?,
    val content: EmotionRegistrationContent,
)

sealed interface EmotionRegistrationContent {
    data object None : EmotionRegistrationContent

    data class Memo(
        val text: String,
    ) : EmotionRegistrationContent

    data class Audio(
        val filePath: String,
    ) : EmotionRegistrationContent
}

sealed interface EmotionRegistrationResult {
    data class Success(
        val id: Long,
    ) : EmotionRegistrationResult

    data object Unavailable : EmotionRegistrationResult

    data object AudioUnavailable : EmotionRegistrationResult
}
