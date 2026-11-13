package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionSearchFilter

interface EmotionRegionMapRepository {
    suspend fun findRegions(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        forceRefresh: Boolean = false,
        filter: EmotionSearchFilter? = null,
    ): EmotionRegionResult

    fun findSnapshot(
        level: EmotionRegionLevel,
        filter: EmotionSearchFilter? = null,
    ): List<EmotionRegion>?
}
