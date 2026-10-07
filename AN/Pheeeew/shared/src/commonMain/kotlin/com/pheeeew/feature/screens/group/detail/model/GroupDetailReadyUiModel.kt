package com.pheeeew.feature.screens.group.detail.model

import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel

sealed interface GroupMoodFeedUiState {
    data object Loading : GroupMoodFeedUiState

    data class LoadFailed(
        val isRetrying: Boolean,
    ) : GroupMoodFeedUiState

    data class Available(
        val posts: List<GroupMoodPostUiModel>,
        val hasMore: Boolean,
        val loadState: GroupMoodFeedLoadState,
    ) : GroupMoodFeedUiState
}

sealed interface GroupMoodFeedLoadState {
    data object Idle : GroupMoodFeedLoadState

    data object Refreshing : GroupMoodFeedLoadState

    data class RefreshFailed(
        val isRetrying: Boolean,
    ) : GroupMoodFeedLoadState

    data object LoadingMore : GroupMoodFeedLoadState

    data class LoadMoreFailed(
        val isRetrying: Boolean,
    ) : GroupMoodFeedLoadState
}

data class GroupDetailReadyUiModel(
    val name: String,
    val role: GroupRole,
    val description: String?,
    val memberCount: Long,
    val stamp: StampAppearanceUiModel,
    val weeklyStampCount: Long,
    val weeklyStampRank: Int?,
    val weeklyEmotionPressCount: Long,
    val weeklyEmotionPressRank: Int?,
    val feed: GroupMoodFeedUiState,
)

fun GroupDetailUiModel.toReadyUiModel(feed: GroupMoodFeedUiState): GroupDetailReadyUiModel =
    GroupDetailReadyUiModel(
        name = group.name,
        role = role,
        description = group.description,
        memberCount = group.memberCount,
        stamp = group.stamp,
        weeklyStampCount = weeklyStampCount,
        weeklyStampRank = weeklyStampRank,
        weeklyEmotionPressCount = weeklyEmotionPressCount,
        weeklyEmotionPressRank = weeklyEmotionPressRank,
        feed = feed,
    )
