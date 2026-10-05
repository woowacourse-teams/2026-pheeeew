package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.repository.EmotionRegionMapRepository

class FindEmotionRegionsUseCase(private val repository: EmotionRegionMapRepository) {
    suspend operator fun invoke(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        groupId: String? = null,
        forceRefresh: Boolean = false,
    ): EmotionRegionResult {
        if (!bounds.isValid()) return EmotionRegionResult.Failure
        return repository.findRegions(bounds, level, groupId, forceRefresh)
    }
}
