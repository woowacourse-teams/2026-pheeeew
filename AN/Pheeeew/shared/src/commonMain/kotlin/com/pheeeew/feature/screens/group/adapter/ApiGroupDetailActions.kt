package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.model.group.GroupDetail
import com.pheeeew.domain.model.group.GroupPressState
import com.pheeeew.domain.repository.group.GroupDetailLookupResult
import com.pheeeew.domain.repository.group.GroupDetailRepository
import com.pheeeew.domain.repository.group.GroupLeaveResult
import com.pheeeew.feature.screens.group.detail.GroupDetailLoadResult
import com.pheeeew.feature.screens.group.detail.GroupDetailSource
import com.pheeeew.feature.screens.group.detail.LeaveGroupAction
import com.pheeeew.feature.screens.group.detail.LeaveGroupResult
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailCopyKey
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupRankUiModel
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
            GroupDetailLookupResult.MembershipChanged -> GroupDetailLoadResult.MembershipChanged
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

private fun GroupDetail.toUiModel(): GroupDetailUiModel {
    val emotionCounts =
        GroupPressState.entries.map { state ->
            EmotionCountUiModel(
                kind = state.toUiKind(),
                count = todayPresses.counts.getValue(state),
            )
        }
    val hasRecordedPress = emotionCounts.any { it.count > 0L }
    val summary =
        if (hasRecordedPress) {
            emotionCounts.maxBy { it.count }.kind.toSummaryKey()
        } else {
            GroupDetailCopyKey.SummaryNeutral
        }
    val presentationKind =
        if (hasRecordedPress) GroupDetailPresentationKind.Active else GroupDetailPresentationKind.Neutral

    return GroupDetailUiModel(
        group = group.toSummaryUiModel(weeklyStampCount = weeklyScore),
        role = group.role,
        emotionCounts = emotionCounts,
        todayTotal = todayPresses.total,
        rank = weeklyRank?.let { GroupRankUiModel.Ranked(it) } ?: GroupRankUiModel.Unranked,
        inviteCode = group.inviteCode,
        presentation =
            GroupDetailPresentationUiModel(
                kind = presentationKind,
                heroTitle =
                    if (presentationKind == GroupDetailPresentationKind.Active) {
                        GroupDetailCopyKey.ActiveHeroTitle
                    } else {
                        GroupDetailCopyKey.NeutralHeroTitle
                    },
                heroSubtitle =
                    if (presentationKind == GroupDetailPresentationKind.Active) {
                        GroupDetailCopyKey.ActiveHeroSubtitle
                    } else {
                        GroupDetailCopyKey.NeutralHeroSubtitle
                    },
                summaryMessage = summary,
            ),
    )
}

private fun GroupPressState.toUiKind(): EmotionKind =
    when (this) {
        GroupPressState.FRUSTRATED -> EmotionKind.Blocked
        GroupPressState.IRRITATED -> EmotionKind.Annoyed
        GroupPressState.EXHAUSTED -> EmotionKind.Tired
        GroupPressState.DISCOURAGED -> EmotionKind.Defeated
        GroupPressState.ANGRY -> EmotionKind.Angry
    }

private fun EmotionKind.toSummaryKey(): GroupDetailCopyKey =
    when (this) {
        EmotionKind.Blocked -> GroupDetailCopyKey.SummaryBlocked
        EmotionKind.Annoyed -> GroupDetailCopyKey.SummaryAnnoyed
        EmotionKind.Tired -> GroupDetailCopyKey.SummaryTired
        EmotionKind.Defeated -> GroupDetailCopyKey.SummaryDefeated
        EmotionKind.Angry -> GroupDetailCopyKey.SummaryAngry
    }
