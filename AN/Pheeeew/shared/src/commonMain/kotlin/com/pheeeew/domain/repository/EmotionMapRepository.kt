package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot

interface EmotionMapRepository {
    suspend fun findPage(
        bounds: EmotionMapBounds?,
        groupId: String?,
        cursor: String?,
        forceRefresh: Boolean = false,
    ): EmotionMapPageResult

    fun findSnapshot(
        bounds: EmotionMapBounds,
        groupId: String? = null,
    ): EmotionMapSnapshot?
}
