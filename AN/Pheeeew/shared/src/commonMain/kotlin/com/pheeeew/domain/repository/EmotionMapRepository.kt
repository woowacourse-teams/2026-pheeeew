package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionSearchFilter

interface EmotionMapRepository {
    suspend fun findPage(
        bounds: EmotionMapBounds?,
        cursor: String?,
        forceRefresh: Boolean = false,
    ): EmotionMapPageResult

    suspend fun findFilteredPage(
        bounds: EmotionMapBounds?,
        filter: EmotionSearchFilter,
        cursor: String?,
        forceRefresh: Boolean = false,
    ): EmotionMapPageResult = findPage(bounds, cursor, forceRefresh)

    fun findSnapshot(bounds: EmotionMapBounds): EmotionMapSnapshot?

    fun findFilteredSnapshot(
        bounds: EmotionMapBounds,
        filter: EmotionSearchFilter,
    ): EmotionMapSnapshot? = findSnapshot(bounds)
}
