package com.pheeeew.feature.screens.group.data

import com.pheeeew.domain.repository.GroupInviteLookupResult
import com.pheeeew.domain.repository.GroupJoinRepository
import com.pheeeew.domain.repository.GroupJoinRepositoryResult
import com.pheeeew.feature.screens.group.home.GroupListResult
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.join.GroupJoinResult
import com.pheeeew.feature.screens.group.join.GroupLookupResult
import com.pheeeew.feature.screens.group.join.JoinGroupAction
import com.pheeeew.feature.screens.group.join.LookupGroupAction
import com.pheeeew.feature.screens.group.model.GroupId

class ApiLookupGroupAction(
    private val repository: GroupJoinRepository,
) : LookupGroupAction {
    override suspend fun find(normalizedCode: String): GroupLookupResult =
        when (val result = repository.findByInviteCode(normalizedCode)) {
            is GroupInviteLookupResult.Found -> GroupLookupResult.Found(result.group.toSummaryUiModel())
            GroupInviteLookupResult.NotFound -> GroupLookupResult.NotFound
            is GroupInviteLookupResult.RateLimited -> GroupLookupResult.RateLimited(result.retryAfterMillis)
            GroupInviteLookupResult.Unavailable -> GroupLookupResult.Unavailable
        }
}

/** Adapts the API result and checks membership before reporting 409 or an uncertain write as joined. */
class ApiJoinGroupAction(
    private val repository: GroupJoinRepository,
    private val groupListSource: GroupListSource,
) : JoinGroupAction {
    override suspend fun join(
        groupId: GroupId,
        normalizedCode: String,
    ): GroupJoinResult =
        when (val result = repository.join(normalizedCode)) {
            is GroupJoinRepositoryResult.Joined -> {
                GroupJoinResult.Joined(GroupId(result.groupId.value))
            }

            GroupJoinRepositoryResult.InviteCodeNotFound -> GroupJoinResult.InviteCodeNotFound

            GroupJoinRepositoryResult.AlreadyMember -> {
                if (isMember(groupId)) GroupJoinResult.Joined(groupId) else GroupJoinResult.AlreadyMember
            }

            is GroupJoinRepositoryResult.RateLimited -> GroupJoinResult.RateLimited(result.retryAfterMillis)
            GroupJoinRepositoryResult.Rejected -> GroupJoinResult.Rejected
            GroupJoinRepositoryResult.Unavailable -> GroupJoinResult.Unavailable
            GroupJoinRepositoryResult.OutcomeUnknown -> {
                if (isMember(groupId)) GroupJoinResult.Joined(groupId) else GroupJoinResult.OutcomeUnknown
            }
        }

    private suspend fun isMember(groupId: GroupId): Boolean =
        when (val result = groupListSource.loadGroups()) {
            is GroupListResult.Success -> result.groups.any { it.id.value.equals(groupId.value, ignoreCase = true) }
            GroupListResult.Unavailable -> false
        }
}
