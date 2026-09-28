package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapFailure
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.repository.EmotionMapRepository

class FindEmotionMapPageUseCase(
    private val repository: EmotionMapRepository,
) {
    suspend operator fun invoke(
        bounds: EmotionMapBounds?,
        groupId: String? = null,
        cursor: String? = null,
        forceRefresh: Boolean = false,
    ): EmotionMapPageResult {
        if (cursor == null && (bounds == null || !bounds.isValid())) {
            return EmotionMapPageResult.Failure(
                EmotionMapFailure.InvalidResponse,
            )
        }
        if (cursor != null && cursor.isBlank()) {
            return EmotionMapPageResult.Failure(
                EmotionMapFailure.InvalidResponse,
            )
        }
        return repository.findPage(bounds, groupId, cursor, forceRefresh)
    }
}
