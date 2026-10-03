package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupPressBatch
import com.pheeeew.domain.model.group.GroupPressCounts

interface GroupPressRepository {
    suspend fun press(
        groupId: GroupId,
        batch: GroupPressBatch,
    ): GroupPressResult
}

sealed interface GroupPressResult {
    data class Pressed(
        val counts: GroupPressCounts,
    ) : GroupPressResult

    data object MembershipChanged : GroupPressResult

    data object NotFound : GroupPressResult

    data class RateLimited(
        val retryAfterMillis: Long?,
    ) : GroupPressResult

    data object Rejected : GroupPressResult

    data object Unavailable : GroupPressResult

    /** The server may have recorded the press; callers must reconcile with a read, never replay the write. */
    data object OutcomeUnknown : GroupPressResult
}
