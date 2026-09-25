package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.model.GroupOperationKey

/** 화면 입력을 기능 경계로 전달하는 콜백 모음입니다. */
data class GroupDetailActions(
    val onBack: () -> Unit,
    val onReturnHome: () -> Unit,
    val onRetry: () -> Unit,
    val onMoreClick: () -> Unit,
    val onInviteClick: () -> Unit,
    val onInviteShareClick: () -> Unit,
    val onCopyCodeClick: () -> Unit,
    val onDismissOverlay: () -> Unit,
    val onLeaveMenuClick: () -> Unit,
    val onConfirmLeave: () -> Unit,
    val onRetryLeave: () -> Unit,
    val onResolveLeaveOutcome: () -> Unit,
    val onEmotionTap: (EmotionKind) -> Boolean,
    val onNoticeDismissed: (GroupOperationKey) -> Unit,
)
