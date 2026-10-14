package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.GroupDetail
import com.pheeeew.domain.model.group.GroupId

interface GroupDetailRepository {
    suspend fun findById(groupId: GroupId): GroupDetailLookupResult

    suspend fun leave(groupId: GroupId): GroupLeaveResult
}

sealed interface GroupDetailLookupResult {
    data class Found(
        val detail: GroupDetail,
    ) : GroupDetailLookupResult

    data object NotFound : GroupDetailLookupResult

    data object Unavailable : GroupDetailLookupResult
}

sealed interface GroupLeaveResult {
    data object Left : GroupLeaveResult

    data object MembershipChanged : GroupLeaveResult

    data object NotFound : GroupLeaveResult

    data object OwnerCannotLeave : GroupLeaveResult

    data object OutcomeUnknown : GroupLeaveResult

    data object Unavailable : GroupLeaveResult
}
