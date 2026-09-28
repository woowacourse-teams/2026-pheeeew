package com.pheeeew.domain.usecase

import com.pheeeew.domain.repository.EmotionModerationRepository
import com.pheeeew.domain.repository.EmotionModerationResult

class BlockEmotionUseCase(
    private val repository: EmotionModerationRepository,
) {
    suspend operator fun invoke(
        emotionId: Long,
        isMine: Boolean,
    ): EmotionModerationResult {
        require(emotionId > 0)
        return if (isMine) repository.blockEmotion(emotionId) else repository.blockUser(emotionId)
    }
}

class DeleteEmotionUseCase(
    private val repository: EmotionModerationRepository,
) {
    suspend operator fun invoke(emotionId: Long): EmotionModerationResult {
        require(emotionId > 0)
        return repository.delete(emotionId)
    }
}

class ReportEmotionUseCase(
    private val repository: EmotionModerationRepository,
) {
    suspend operator fun invoke(
        emotionId: Long,
        reason: String,
    ): EmotionModerationResult {
        require(emotionId > 0)
        require(reason.isNotBlank())
        return repository.report(emotionId, reason)
    }
}
