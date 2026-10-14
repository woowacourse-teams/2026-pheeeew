package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.GroupMembersPage
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

interface GroupMembersRepository {
    suspend fun loadPage(
        groupId: DomainGroupId,
        cursor: String?,
    ): GroupMembersResult
}

sealed interface GroupMembersResult {
    data class Loaded(
        val page: GroupMembersPage,
    ) : GroupMembersResult

    data object Forbidden : GroupMembersResult

    data object NotFound : GroupMembersResult

    data object InvalidCursor : GroupMembersResult

    data object Unauthorized : GroupMembersResult

    data object Unavailable : GroupMembersResult
}
