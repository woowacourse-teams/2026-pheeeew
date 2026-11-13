package com.pheeeew.domain.repository

fun interface ReportRepository {
    suspend fun report(
        emotionId: Long,
        reason: String,
    ): EmotionModerationResult
}
