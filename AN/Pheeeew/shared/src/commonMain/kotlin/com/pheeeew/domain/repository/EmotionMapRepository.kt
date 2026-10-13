package com.pheeeew.domain.repository

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionSearchFilter

interface EmotionMapRepository {
    suspend fun findPage(
        bounds: EmotionMapBounds?,
        groupId: String?,
        cursor: String?,
        forceRefresh: Boolean = false,
    ): EmotionMapPageResult

    suspend fun findFilteredPage(
        bounds: EmotionMapBounds?,
        filter: EmotionSearchFilter,
        cursor: String?,
        forceRefresh: Boolean = false,
    ): EmotionMapPageResult = findPage(bounds, filter.groupId, cursor, forceRefresh)

    fun findSnapshot(
        bounds: EmotionMapBounds,
        groupId: String? = null,
    ): EmotionMapSnapshot?

    fun findFilteredSnapshot(
        bounds: EmotionMapBounds,
        filter: EmotionSearchFilter,
    ): EmotionMapSnapshot? = findSnapshot(bounds, filter.groupId)
}
