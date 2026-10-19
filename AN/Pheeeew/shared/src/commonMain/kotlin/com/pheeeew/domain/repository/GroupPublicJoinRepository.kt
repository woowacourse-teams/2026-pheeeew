package com.pheeeew.domain.repository

import com.pheeeew.domain.model.group.GroupId

fun interface GroupPublicJoinRepository {
    suspend fun join(groupId: GroupId): GroupPublicJoinResult
}

sealed interface GroupPublicJoinResult {
    data object Joined : GroupPublicJoinResult

    data object AlreadyMember : GroupPublicJoinResult

    data object PrivateGroup : GroupPublicJoinResult

    data object NotFound : GroupPublicJoinResult

    data object Unavailable : GroupPublicJoinResult

    data object OutcomeUnknown : GroupPublicJoinResult
}
