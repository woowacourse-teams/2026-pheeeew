package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.repository.EmotionRegionMapRepository

class FindEmotionRegionsUseCase(
    private val repository: EmotionRegionMapRepository,
) {
    suspend operator fun invoke(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        forceRefresh: Boolean = false,
        filter: EmotionSearchFilter? = null,
    ): EmotionRegionResult {
        if (!bounds.isValid()) return EmotionRegionResult.Failure
        return repository.findRegions(bounds, level, forceRefresh, filter)
    }
}
