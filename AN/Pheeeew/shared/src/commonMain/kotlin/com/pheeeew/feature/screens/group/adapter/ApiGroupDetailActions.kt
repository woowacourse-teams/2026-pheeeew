package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.model.group.GroupDetail
import com.pheeeew.domain.repository.group.GroupDetailLookupResult
import com.pheeeew.domain.repository.group.GroupDetailRepository
import com.pheeeew.domain.repository.group.GroupLeaveResult
import com.pheeeew.feature.screens.group.detail.GroupDetailLoadResult
import com.pheeeew.feature.screens.group.detail.GroupDetailSource
import com.pheeeew.feature.screens.group.detail.LeaveGroupAction
import com.pheeeew.feature.screens.group.detail.LeaveGroupResult
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.mapper.toSummaryUiModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiGroupDetailSource(
    private val repository: GroupDetailRepository,
) : GroupDetailSource {
    override suspend fun load(groupId: GroupId): GroupDetailLoadResult {
        val domainGroupId =
            DomainGroupId.parse(groupId.value)
                ?: return GroupDetailLoadResult.Unavailable
        return when (val result = repository.findById(domainGroupId)) {
            is GroupDetailLookupResult.Found -> GroupDetailLoadResult.Loaded(result.detail.toUiModel())
            GroupDetailLookupResult.NotFound -> GroupDetailLoadResult.NotFound
            GroupDetailLookupResult.Unavailable -> GroupDetailLoadResult.Unavailable
        }
    }
}

class ApiLeaveGroupAction(
    private val repository: GroupDetailRepository,
) : LeaveGroupAction {
    override suspend fun leave(groupId: GroupId): LeaveGroupResult {
        val domainGroupId =
            DomainGroupId.parse(groupId.value)
                ?: return LeaveGroupResult.Unavailable
        return when (repository.leave(domainGroupId)) {
            GroupLeaveResult.Left -> LeaveGroupResult.Left
            GroupLeaveResult.MembershipChanged -> LeaveGroupResult.MembershipChanged
            GroupLeaveResult.NotFound -> LeaveGroupResult.NotFound
            GroupLeaveResult.OwnerCannotLeave -> LeaveGroupResult.OwnerCannotLeave
            GroupLeaveResult.OutcomeUnknown -> LeaveGroupResult.OutcomeUnknown
            GroupLeaveResult.Unavailable -> LeaveGroupResult.Unavailable
        }
    }
}

private fun GroupDetail.toUiModel() =
    GroupDetailUiModel(
        group = group.toSummaryUiModel(),
        role = group.role,
        inviteCode = group.inviteCode,
        weeklyStampCount = weeklyStampCount,
        weeklyStampRank = weeklyStampRank,
        weeklyEmotionPressCount = weeklyEmotionPressCount,
        weeklyEmotionPressRank = weeklyEmotionPressRank,
    )
