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

    /** The request was definitely not applied. */
    data object Rejected : EmotionRegistrationResult

    /** Application of the write could not be confirmed. */
    data object Unavailable : EmotionRegistrationResult

    data object AudioUnavailable : EmotionRegistrationResult

    data object AudioUploadFailed : EmotionRegistrationResult
}
