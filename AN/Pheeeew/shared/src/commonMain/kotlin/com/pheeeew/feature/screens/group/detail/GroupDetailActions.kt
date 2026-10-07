package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.feature.screens.group.model.GroupOperationKey

data class GroupDetailActions(
    val onBack: () -> Unit,
    val onReturnHome: () -> Unit,
    val onRetry: () -> Unit,
    val onMoreClick: () -> Unit,
    val onInviteClick: () -> Unit,
    val onCopyCodeClick: () -> Unit,
    val onDismissOverlay: () -> Unit,
    val onLeaveMenuClick: () -> Unit,
    val onConfirmLeave: () -> Unit,
    val onRetryLeave: () -> Unit,
    val onResolveLeaveOutcome: () -> Unit,
    val onNoticeDismissed: (GroupOperationKey) -> Unit,
    val onMoodReactionClick: ((String, EmotionReactionType) -> Unit)?,
    val onMoodAudioClick: ((String) -> Unit)?,
    val onMoodBlockClick: ((String) -> Unit)?,
    val onMoodReportClick: ((String) -> Unit)?,
    val onMoodFeedRetry: (() -> Unit)?,
    val onMoodFeedLoadMore: (() -> Unit)?,
)
