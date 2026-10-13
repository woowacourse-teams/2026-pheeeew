package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.group.GroupMembersRepository
import com.pheeeew.domain.repository.group.GroupMembersResult
import com.pheeeew.feature.screens.group.members.GroupMemberUiModel
import com.pheeeew.feature.screens.group.members.GroupMembersPage
import com.pheeeew.feature.screens.group.members.GroupMembersPageResult
import com.pheeeew.feature.screens.group.members.GroupMembersSource
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiGroupMembersSource(
    private val repository: GroupMembersRepository,
) : GroupMembersSource {
    override suspend fun loadPage(
        groupId: GroupId,
        cursor: String?,
    ): GroupMembersPageResult {
        val id = DomainGroupId.parse(groupId.value) ?: return GroupMembersPageResult.NotFound
        return when (val result = repository.loadPage(id, cursor)) {
            is GroupMembersResult.Loaded -> {
                GroupMembersPageResult.Success(
                    GroupMembersPage(
                        result.page.items.map {
                            GroupMemberUiModel(it.nickname, it.role, it.joinedAt)
                        },
                        result.page.nextCursor,
                    ),
                )
            }

            GroupMembersResult.Forbidden -> {
                GroupMembersPageResult.Forbidden
            }

            GroupMembersResult.NotFound -> {
                GroupMembersPageResult.NotFound
            }

            GroupMembersResult.InvalidCursor -> {
                GroupMembersPageResult.InvalidCursor
            }

            GroupMembersResult.Unauthorized -> {
                GroupMembersPageResult.Unauthorized
            }

            GroupMembersResult.Unavailable -> {
                GroupMembersPageResult.Unavailable
            }
        }
    }
}
