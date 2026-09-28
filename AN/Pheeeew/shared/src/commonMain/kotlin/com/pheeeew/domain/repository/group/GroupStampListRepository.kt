package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.GroupStampItem

fun interface GroupStampListRepository {
    suspend fun findMyStamps(): GroupStampListLoadResult

    /** Discards membership snapshots after joining, creating, or leaving a group. */
    fun invalidate() = Unit
}

sealed interface GroupStampListLoadResult {
    data class Loaded(
        val groups: List<GroupStampItem>,
    ) : GroupStampListLoadResult

    data object Unavailable : GroupStampListLoadResult
}
