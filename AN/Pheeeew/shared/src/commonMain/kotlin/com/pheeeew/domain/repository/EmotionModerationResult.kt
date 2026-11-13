package com.pheeeew.domain.repository

sealed interface EmotionModerationResult {
    data object Success : EmotionModerationResult

    data object NotFound : EmotionModerationResult

    data object OwnEmotion : EmotionModerationResult

    data object AuthorUnknown : EmotionModerationResult

    data object NetworkUnavailable : EmotionModerationResult

    data object Unavailable : EmotionModerationResult
}
