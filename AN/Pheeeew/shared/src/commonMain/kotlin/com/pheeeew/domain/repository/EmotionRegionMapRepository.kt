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
        groupId: String? = null,
        forceRefresh: Boolean = false,
        filter: EmotionSearchFilter? = null,
    ): EmotionRegionResult

    fun findSnapshot(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        groupId: String? = null,
        filter: EmotionSearchFilter? = null,
    ): List<EmotionRegion>?
}
