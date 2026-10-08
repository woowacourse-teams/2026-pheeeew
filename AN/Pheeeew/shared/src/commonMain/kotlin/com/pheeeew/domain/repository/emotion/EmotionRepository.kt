package com.pheeeew.domain.repository.emotion

import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType

sealed interface EmotionResult<out T> {
    data class Success<T>(
        val value: T,
    ) : EmotionResult<T>

    data class Failure(
        val reason: EmotionFailure,
    ) : EmotionResult<Nothing>
}

enum class EmotionFailure { UNAVAILABLE, NOT_FOUND, INVALID_REQUEST, AUTHENTICATION, FORBIDDEN }

interface EmotionRepository {
    suspend fun firstPage(
        bounds: EmotionBounds,
        groupId: String?,
    ): EmotionResult<EmotionPage>

    suspend fun nextPage(cursor: String): EmotionResult<EmotionPage>

    suspend fun feedPage(
        groupId: String,
        cursor: String?,
    ): EmotionResult<EmotionPage>

    suspend fun detail(id: Long): EmotionResult<Emotion>

    suspend fun react(
        id: Long,
        type: EmotionReactionType,
        selected: Boolean,
    ): EmotionResult<Unit>

    suspend fun block(id: Long): EmotionResult<Unit>
}
