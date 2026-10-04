package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.GroupStampItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

fun interface GroupStampListRepository {
    suspend fun findMyStamps(): GroupStampListLoadResult

    /** Emits a monotonically increasing revision whenever membership snapshots are invalidated. */
    val membershipChanges: Flow<Long>
        get() = emptyFlow()

    /** Discards membership snapshots after joining, creating, or leaving a group. */
    fun invalidate() = Unit
}

sealed interface GroupStampListLoadResult {
    data class Loaded(
        val groups: List<GroupStampItem>,
    ) : GroupStampListLoadResult

    data object Unavailable : GroupStampListLoadResult
}
