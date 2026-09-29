package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.EmotionDetailResult
import com.pheeeew.domain.model.emotion.EmotionReactionType

interface EmotionDetailRepository {
    suspend fun findById(id: Long): EmotionDetailResult

    suspend fun setReactionSelected(
        emotionId: Long,
        type: EmotionReactionType,
        selected: Boolean,
    ): Boolean
}
