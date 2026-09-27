package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.model.group.GroupPressState
import com.pheeeew.domain.repository.group.GroupPressRepository
import com.pheeeew.domain.repository.group.GroupPressResult
import com.pheeeew.feature.screens.group.detail.GroupPressSnapshotUiModel
import com.pheeeew.feature.screens.group.detail.PressGroupEmotionAction
import com.pheeeew.feature.screens.group.detail.PressGroupEmotionResult
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiGroupPressAction(
    private val repository: GroupPressRepository,
) : PressGroupEmotionAction {
    override suspend fun press(
        groupId: GroupId,
        emotion: EmotionKind,
    ): PressGroupEmotionResult {
        val domainGroupId = DomainGroupId.parse(groupId.value) ?: return PressGroupEmotionResult.Unavailable
        return when (val result = repository.press(domainGroupId, emotion.toDomainState())) {
            is GroupPressResult.Pressed -> {
                val snapshot = result.counts
                PressGroupEmotionResult.Pressed(
                    GroupPressSnapshotUiModel(
                        emotionCounts =
                            GroupPressState.entries.map { state ->
                                EmotionCountUiModel(state.toUiKind(), snapshot.counts.getValue(state))
                            },
                        total = snapshot.total,
                    ),
                )
            }

            GroupPressResult.MembershipChanged -> {
                PressGroupEmotionResult.MembershipChanged
            }

            GroupPressResult.NotFound -> {
                PressGroupEmotionResult.NotFound
            }

            is GroupPressResult.RateLimited -> {
                PressGroupEmotionResult.RateLimited(result.retryAfterMillis)
            }

            GroupPressResult.Rejected -> {
                PressGroupEmotionResult.Rejected
            }

            GroupPressResult.Unavailable -> {
                PressGroupEmotionResult.Unavailable
            }

            GroupPressResult.OutcomeUnknown -> {
                PressGroupEmotionResult.OutcomeUnknown
            }
        }
    }
}

private fun EmotionKind.toDomainState(): GroupPressState =
    when (this) {
        EmotionKind.Blocked -> GroupPressState.FRUSTRATED
        EmotionKind.Annoyed -> GroupPressState.IRRITATED
        EmotionKind.Tired -> GroupPressState.EXHAUSTED
        EmotionKind.Defeated -> GroupPressState.DISCOURAGED
        EmotionKind.Angry -> GroupPressState.ANGRY
    }

private fun GroupPressState.toUiKind(): EmotionKind =
    when (this) {
        GroupPressState.FRUSTRATED -> EmotionKind.Blocked
        GroupPressState.IRRITATED -> EmotionKind.Annoyed
        GroupPressState.EXHAUSTED -> EmotionKind.Tired
        GroupPressState.DISCOURAGED -> EmotionKind.Defeated
        GroupPressState.ANGRY -> EmotionKind.Angry
    }
