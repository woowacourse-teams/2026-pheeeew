package com.pheeeew.domain.usecase

import com.pheeeew.domain.repository.BlockRepository
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.repository.EmotionRepository
import com.pheeeew.domain.repository.ReportRepository

class BlockUserUseCase(
    private val repository: BlockRepository,
) {
    suspend operator fun invoke(emotionId: Long): EmotionModerationResult {
        require(emotionId > 0)
        return repository.blockUser(emotionId)
    }
}

class DeleteEmotionUseCase(
    private val repository: EmotionRepository,
) {
    suspend operator fun invoke(emotionId: Long): EmotionModerationResult {
        require(emotionId > 0)
        return repository.delete(emotionId)
    }
}

class ReportEmotionUseCase(
    private val repository: ReportRepository,
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
