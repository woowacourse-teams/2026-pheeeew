package com.pheeeew.domain.repository

interface EmotionModerationRepository {
    suspend fun report(
        emotionId: Long,
        reason: String,
    ): EmotionModerationResult

    suspend fun blockEmotion(emotionId: Long): EmotionModerationResult

    suspend fun blockUser(emotionId: Long): EmotionModerationResult

}

sealed interface EmotionModerationResult {
    data object Success : EmotionModerationResult

    data object NotFound : EmotionModerationResult

    data object OwnEmotion : EmotionModerationResult

    data object AuthorUnknown : EmotionModerationResult

    data object NetworkUnavailable : EmotionModerationResult

    data object Unavailable : EmotionModerationResult
}
