package com.pheeeew.domain.repository

interface BlockRepository {
    suspend fun blockEmotion(emotionId: Long): EmotionModerationResult

    suspend fun blockUser(emotionId: Long): EmotionModerationResult
}
