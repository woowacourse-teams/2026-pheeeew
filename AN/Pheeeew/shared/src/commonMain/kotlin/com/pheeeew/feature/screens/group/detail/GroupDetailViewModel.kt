package com.pheeeew.feature.screens.group.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.withDominantEmotionSummary
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 한 그룹 ID의 상세 상태와 팝업·나가기·복사 결과를 소유합니다. */
class GroupDetailViewModel(
    val groupId: GroupId,
    private val dependencies: GroupDetailDependencies,
    initialGroupName: String? = null,
) : ViewModel() {
    val telemetry = ProductMonitoring(dependencies.monitoring, "group_detail", labels("group_key" to groupId.value))
    private val _uiState =
        MutableStateFlow(
            GroupDetailUiState(
                groupName = initialGroupName,
            ),
        )
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var leaveJob: Job? = null
    private var pressJob: Job? = null
    private val queuedEmotionPresses = ArrayDeque<Pair<EmotionKind, GroupOperationKey>>()
    var lastAcceptedPressKey: GroupOperationKey? = null
        private set
    private var loadGeneration = 0L
    private var pressGeneration = 0L
    private var hasResumed = false

    init {
        loadDetail()
    }

    fun onResumed() {
        // 첫 화면 활성화는 init의 조회를 사용하고, 이후 복귀할 때만 다시 조회합니다.
        if (!hasResumed) {
            hasResumed = true
            return
        }
        onRefresh(showRefreshIndicator = false)
    }

    fun onRetry() {
        val state = _uiState.value
        if (state.overlay != GroupDetailOverlay.None || state.content is GroupDetailContent.MembershipChanged ||
            state.content is GroupDetailContent.NotFound
        ) {
            return
        }
        onRefresh()
    }

    fun onRefresh(showRefreshIndicator: Boolean = true) {
        val state = _uiState.value
        if (state.overlay != GroupDetailOverlay.None || state.content is GroupDetailContent.MembershipChanged ||
            state.content is GroupDetailContent.NotFound
        ) {
            return
        }
        when (state.pressStatus) {
            is GroupPressStatus.Sending,
            is GroupPressStatus.Reconciling,
            -> {
                return
            }

            is GroupPressStatus.OutcomeUnknown -> {
                onResolvePressOutcome()
                return
            }

            GroupPressStatus.Idle -> {
                Unit
            }
        }
        loadDetail(showRefreshIndicator = showRefreshIndicator)
    }

    /** Accepts taps while a press request is in flight and submits them in order. */
    fun onEmotionTap(emotion: EmotionKind): Boolean {
        lastAcceptedPressKey = null
        val current = _uiState.value
        if (current.detail?.group?.id != groupId || current.overlay != GroupDetailOverlay.None) return false

        when (current.pressStatus) {
            is GroupPressStatus.Sending -> {
                val operationKey = dependencies.operationKeyAllocator.next()
                lastAcceptedPressKey = operationKey
                queuedEmotionPresses.addLast(emotion to operationKey)
                _uiState.update { state -> state.withPendingPress(emotion, 1) }
                return true
            }

            GroupPressStatus.Idle -> {
                val operationKey = dependencies.operationKeyAllocator.next()
                lastAcceptedPressKey = operationKey
                _uiState.update { state -> state.withPendingPress(emotion, 1) }
                if (pressJob?.isActive == true) {
                    queuedEmotionPresses.addLast(emotion to operationKey)
                } else {
                    submitEmotionPress(emotion, operationKey)
                }
                return true
            }

            is GroupPressStatus.Reconciling,
            is GroupPressStatus.OutcomeUnknown,
            -> {
                return false
            }
        }
    }

    private fun submitEmotionPress(
        emotion: EmotionKind,
        operationKey: GroupOperationKey,
    ) {
        val current = _uiState.value
        if (current.detail?.group?.id != groupId || current.pressStatus != GroupPressStatus.Idle) {
            return
        }

        val requestGeneration = ++pressGeneration
        invalidateLoad()
        _uiState.update { state ->
            if (state.pressStatus == GroupPressStatus.Idle && state.detail?.group?.id == groupId) {
                state.copy(pressStatus = GroupPressStatus.Sending(operationKey, emotion))
            } else {
                state
            }
        }
        if ((_uiState.value.pressStatus as? GroupPressStatus.Sending)?.operationKey != operationKey) return

        pressJob =
            viewModelScope.launch {
                try {
                    val result =
                        try {
                            withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                                telemetry
                                    .operation(
                                        "group_emotion_press_finished",
                                        labels(
                                            "group_operation_key" to
                                                "${operationKey.ownerInstanceId}:${operationKey.sequence}",
                                        ),
                                        started = "group_emotion_press_started",
                                    ).observe(
                                        ::resultLabel,
                                    ) { dependencies.pressGroupEmotionAction.press(groupId, emotion) }
                            } ?: PressGroupEmotionResult.OutcomeUnknown
                        } catch (cancellation: CancellationException) {
                            _uiState.update { state ->
                                if ((state.pressStatus as? GroupPressStatus.Sending)?.operationKey == operationKey) {
                                    state.copy(pressStatus = GroupPressStatus.OutcomeUnknown(operationKey, emotion))
                                } else {
                                    state
                                }
                            }
                            throw cancellation
                        } catch (exception: Exception) {
                            dependencies.errorReporter.reportUnexpected(exception)
                            PressGroupEmotionResult.OutcomeUnknown
                        }
                    if (requestGeneration != pressGeneration) {
                        return@launch
                    }
                    handlePressResult(operationKey, emotion, result)
                    if (result is PressGroupEmotionResult.RateLimited) {
                        delay((result.retryAfterMillis ?: DEFAULT_PRESS_RETRY_DELAY_MILLIS).coerceAtLeast(0L))
                    }
                } finally {
                    if (pressJob === currentCoroutineContext()[Job]) {
                        pressJob = null
                        submitNextQueuedEmotionPress()
                    }
                }
            }
    }

    private fun submitNextQueuedEmotionPress() {
        val state = _uiState.value
        if (pressJob?.isActive == true || state.pressStatus != GroupPressStatus.Idle) return
        if (state.overlay is GroupDetailOverlay.Leaving || state.overlay is GroupDetailOverlay.Left) return
        if (state.detail?.group?.id != groupId ||
            state.content is GroupDetailContent.MembershipChanged ||
            state.content is GroupDetailContent.NotFound
        ) {
            queuedEmotionPresses.clear()
            _uiState.update { it.copy(pendingEmotionPresses = emptyMap()) }
            return
        }
        val (emotion, operationKey) = queuedEmotionPresses.removeFirstOrNull() ?: return
        submitEmotionPress(emotion, operationKey)
    }

    fun onMoreClick() {
        _uiState.update { state ->
            if (state.detail == null || state.overlay != GroupDetailOverlay.None) {
                state
            } else {
                state.copy(overlay = GroupDetailOverlay.Menu)
            }
        }
    }

    fun onInviteClick() {
        _uiState.update { state ->
            if (state.detail == null || state.overlay != GroupDetailOverlay.None) {
                state
            } else {
                state.copy(overlay = GroupDetailOverlay.InviteCode, copyRequest = null)
            }
        }
    }

    fun onLeaveMenuClick() {
        if (hasPendingEmotionPresses(_uiState.value)) {
            _uiState.update { state ->
                if (state.overlay == GroupDetailOverlay.Menu) state.copy(overlay = GroupDetailOverlay.None) else state
            }
            showNotice(GroupDetailNoticeKind.PressBlockedWhilePending)
            return
        }
        _uiState.update { state ->
            val detail = state.detail
            if (detail == null || state.overlay != GroupDetailOverlay.Menu) {
                state
            } else if (detail.role == GroupRole.OWNER) {
                state.copy(overlay = GroupDetailOverlay.OwnerCannotLeave)
            } else {
                state.copy(overlay = GroupDetailOverlay.LeaveConfirm)
            }
        }
    }

    private fun hasPendingEmotionPresses(state: GroupDetailUiState): Boolean =
        state.pressStatus != GroupPressStatus.Idle ||
            pressJob?.isActive == true ||
            queuedEmotionPresses.isNotEmpty() ||
            state.pendingEmotionPresses.isNotEmpty()

    fun onDismissOverlay() {
        _uiState.update { state ->
            when (state.overlay) {
                GroupDetailOverlay.Menu,
                GroupDetailOverlay.InviteCode,
                GroupDetailOverlay.LeaveConfirm,
                GroupDetailOverlay.LeaveFailed,
                GroupDetailOverlay.LeaveStillMember,
                GroupDetailOverlay.OwnerCannotLeave,
                GroupDetailOverlay.LeaveOutcomeUnknown,
                -> {
                    state.copy(overlay = GroupDetailOverlay.None, copyRequest = null)
                }

                GroupDetailOverlay.None,
                is GroupDetailOverlay.Leaving,
                is GroupDetailOverlay.Left,
                -> {
                    state
                }
            }
        }
    }

    /** false면 화면 종료 대신 열린 팝업을 닫거나 제출을 유지합니다. */
    fun onBackRequested(): Boolean =
        when (_uiState.value.overlay) {
            GroupDetailOverlay.None -> {
                true
            }

            GroupDetailOverlay.Menu,
            GroupDetailOverlay.InviteCode,
            GroupDetailOverlay.LeaveConfirm,
            GroupDetailOverlay.LeaveFailed,
            GroupDetailOverlay.LeaveStillMember,
            GroupDetailOverlay.OwnerCannotLeave,
            GroupDetailOverlay.LeaveOutcomeUnknown,
            -> {
                onDismissOverlay()
                false
            }

            is GroupDetailOverlay.Leaving,
            is GroupDetailOverlay.Left,
            -> {
                false
            }
        }

    fun onCopyCodeClick() {
        val current = _uiState.value
        val code = current.detail?.inviteCode
        if (current.overlay != GroupDetailOverlay.InviteCode || code == null || current.copyRequest != null) return
        val request = GroupCopyCodeRequest(dependencies.operationKeyAllocator.next(), code)
        _uiState.update { state ->
            if (state.overlay != GroupDetailOverlay.InviteCode || state.detail?.inviteCode != code ||
                state.copyRequest != null
            ) {
                state
            } else {
                state.copy(copyRequest = request)
            }
        }
    }

    fun onCopyResult(
        operationKey: GroupOperationKey,
        result: GroupCopyCodeResult,
    ) {
        if (_uiState.value.copyRequest?.operationKey == operationKey &&
            _uiState.value.overlay == GroupDetailOverlay.InviteCode
        ) {
            telemetry.emit(
                "group_invite_copy_finished",
                labels("outcome" to if (result == GroupCopyCodeResult.Copied) "success" else "failed"),
            )
        }
        _uiState.update { state ->
            val request = state.copyRequest
            if (state.overlay != GroupDetailOverlay.InviteCode || request?.operationKey != operationKey) {
                state
            } else {
                state.copy(
                    copyRequest = null,
                    overlay = GroupDetailOverlay.None,
                    notice =
                        GroupDetailNotice(
                            operationKey = operationKey,
                            kind =
                                if (result == GroupCopyCodeResult.Copied) {
                                    GroupDetailNoticeKind.CopySucceeded
                                } else {
                                    GroupDetailNoticeKind.CopyFailed
                                },
                        ),
                )
            }
        }
    }

    fun acknowledgeNotice(operationKey: GroupOperationKey) {
        _uiState.update { state ->
            if (state.notice?.operationKey == operationKey) state.copy(notice = null) else state
        }
    }

    fun onConfirmLeave() {
        submitLeave(expectedOverlay = GroupDetailOverlay.LeaveConfirm)
    }

    fun onRetryLeave() {
        when (val overlay = _uiState.value.overlay) {
            GroupDetailOverlay.LeaveFailed,
            GroupDetailOverlay.LeaveStillMember,
            -> submitLeave(expectedOverlay = overlay)

            else -> Unit
        }
    }

    /** 결과가 불명확할 때 쓰기 요청을 재전송하지 않고 상세/멤버십을 다시 조회합니다. */
    fun onResolveLeaveOutcome() {
        if (_uiState.value.overlay != GroupDetailOverlay.LeaveOutcomeUnknown) return
        loadDetail(reconcileLeaveOutcome = true)
    }

    /** Resolves an uncertain press with a read; the POST is never submitted again. */
    fun onResolvePressOutcome() {
        val status = _uiState.value.pressStatus as? GroupPressStatus.OutcomeUnknown ?: return
        if (loadJob?.isActive == true) return
        _uiState.update { state ->
            if ((state.pressStatus as? GroupPressStatus.OutcomeUnknown)?.operationKey == status.operationKey) {
                state.copy(pressStatus = GroupPressStatus.Reconciling(status.operationKey, status.emotion))
            } else {
                state
            }
        }
        loadDetail(reconcilePressOperationKey = status.operationKey)
    }

    fun acknowledgeLeft(operationKey: GroupOperationKey) {
        _uiState.update { state ->
            if ((state.overlay as? GroupDetailOverlay.Left)?.operationKey == operationKey) {
                state.copy(overlay = GroupDetailOverlay.None)
            } else {
                state
            }
        }
    }

    fun acknowledgeMembershipEvent(operationKey: GroupOperationKey) {
        _uiState.update { state ->
            if (state.membershipEvent?.operationKey == operationKey) state.copy(membershipEvent = null) else state
        }
    }

    private fun loadDetail(
        reconcileLeaveOutcome: Boolean = false,
        reconcilePressOperationKey: GroupOperationKey? = null,
        showRefreshIndicator: Boolean = true,
    ) {
        if (loadJob?.isActive == true) return
        val currentPressStatus = _uiState.value.pressStatus
        if (currentPressStatus is GroupPressStatus.Sending ||
            (
                currentPressStatus is GroupPressStatus.Reconciling &&
                    currentPressStatus.operationKey != reconcilePressOperationKey
            )
        ) {
            return
        }

        val requestId = ++loadGeneration
        val hasSnapshot = _uiState.value.detail != null
        _uiState.update { state ->
            state.copy(
                content = if (hasSnapshot) state.content else GroupDetailContent.Loading,
                refreshStatus =
                    if (hasSnapshot && showRefreshIndicator) {
                        GroupDetailRefreshStatus.Refreshing
                    } else {
                        GroupDetailRefreshStatus.Idle
                    },
            )
        }

        loadJob =
            viewModelScope.launch {
                try {
                    val result =
                        telemetry
                            .operation(
                                "group_detail_load_finished",
                            ).observe(::resultLabel) { requestDetail() }
                    if (reconcileLeaveOutcome || reconcilePressOperationKey != null) {
                        telemetry.emit(
                            "operation_reconciled",
                            labels(
                                "operation_kind" to if (reconcileLeaveOutcome) "group_leave" else "group_press",
                                "outcome" to resultLabel(result),
                            ),
                        )
                    }
                    if (requestId != loadGeneration) return@launch

                    if (result is GroupDetailLoadResult.Loaded && result.detail.group.id != groupId) {
                        dependencies.errorReporter.reportUnexpected(
                            IllegalStateException("상세 공급자가 요청한 그룹과 다른 ID를 반환했습니다."),
                        )
                    }

                    val membershipEvent =
                        when (result) {
                            GroupDetailLoadResult.MembershipChanged -> {
                                GroupDetailMembershipEvent(
                                    operationKey = dependencies.operationKeyAllocator.next(),
                                    reason = GroupDetailAccessLoss.MembershipChanged,
                                )
                            }

                            GroupDetailLoadResult.NotFound -> {
                                GroupDetailMembershipEvent(
                                    operationKey = dependencies.operationKeyAllocator.next(),
                                    reason = GroupDetailAccessLoss.NotFound,
                                )
                            }

                            is GroupDetailLoadResult.Loaded,
                            GroupDetailLoadResult.Unavailable,
                            -> {
                                null
                            }
                        }

                    _uiState.update { state ->
                        when (result) {
                            is GroupDetailLoadResult.Loaded -> {
                                val detail =
                                    result.detail
                                        .withDominantEmotionSummary(state.detail?.presentation?.summaryMessage)
                                        .ownedSnapshot()
                                if (detail.group.id != groupId) {
                                    state.copy(
                                        content =
                                            if (state.detail ==
                                                null
                                            ) {
                                                GroupDetailContent.LoadFailed
                                            } else {
                                                state.content
                                            },
                                        refreshStatus =
                                            if (state.detail == null) {
                                                GroupDetailRefreshStatus.Idle
                                            } else {
                                                GroupDetailRefreshStatus.Failed
                                            },
                                        pressStatus =
                                            state.pressStatus.afterPressReconciliation(
                                                reconcilePressOperationKey,
                                                false,
                                            ),
                                    )
                                } else {
                                    state.copy(
                                        content = GroupDetailContent.Ready(detail),
                                        groupName = detail.group.name,
                                        refreshStatus = GroupDetailRefreshStatus.Idle,
                                        pendingEmotionPresses =
                                            if (reconcilePressOperationKey != null) {
                                                val reconciliation = state.pressStatus as? GroupPressStatus.Reconciling
                                                if (reconciliation?.operationKey == reconcilePressOperationKey) {
                                                    state.pendingEmotionPresses.without(reconciliation.emotion)
                                                } else {
                                                    state.pendingEmotionPresses
                                                }
                                            } else {
                                                state.pendingEmotionPresses
                                            },
                                        pressStatus =
                                            state.pressStatus.afterPressReconciliation(
                                                reconcilePressOperationKey,
                                                true,
                                            ),
                                        overlay =
                                            if (reconcileLeaveOutcome &&
                                                state.overlay == GroupDetailOverlay.LeaveOutcomeUnknown
                                            ) {
                                                GroupDetailOverlay.LeaveStillMember
                                            } else {
                                                state.overlay
                                            },
                                    )
                                }
                            }

                            GroupDetailLoadResult.MembershipChanged -> {
                                state.copy(
                                    content = GroupDetailContent.MembershipChanged,
                                    refreshStatus = GroupDetailRefreshStatus.Idle,
                                    overlay = GroupDetailOverlay.None,
                                    copyRequest = null,
                                    pendingEmotionPresses = emptyMap(),
                                    membershipEvent = membershipEvent,
                                    pressStatus =
                                        state.pressStatus.afterPressReconciliation(
                                            reconcilePressOperationKey,
                                            true,
                                        ),
                                )
                            }

                            GroupDetailLoadResult.NotFound -> {
                                state.copy(
                                    content = GroupDetailContent.NotFound,
                                    refreshStatus = GroupDetailRefreshStatus.Idle,
                                    overlay = GroupDetailOverlay.None,
                                    copyRequest = null,
                                    pendingEmotionPresses = emptyMap(),
                                    membershipEvent = membershipEvent,
                                    pressStatus =
                                        state.pressStatus.afterPressReconciliation(
                                            reconcilePressOperationKey,
                                            true,
                                        ),
                                )
                            }

                            GroupDetailLoadResult.Unavailable -> {
                                state.copy(
                                    content =
                                        if (state.detail ==
                                            null
                                        ) {
                                            GroupDetailContent.LoadFailed
                                        } else {
                                            state.content
                                        },
                                    refreshStatus =
                                        if (state.detail == null) {
                                            GroupDetailRefreshStatus.Idle
                                        } else {
                                            GroupDetailRefreshStatus.Failed
                                        },
                                    pressStatus =
                                        state.pressStatus.afterPressReconciliation(
                                            reconcilePressOperationKey,
                                            false,
                                        ),
                                )
                            }
                        }
                    }
                } finally {
                    if (requestId == loadGeneration) {
                        loadJob = null
                        if (reconcilePressOperationKey != null) submitNextQueuedEmotionPress()
                    }
                }
            }
    }

    private suspend fun requestDetail(): GroupDetailLoadResult =
        try {
            withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                dependencies.source.load(groupId)
            } ?: GroupDetailLoadResult.Unavailable
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            dependencies.errorReporter.reportUnexpected(exception)
            GroupDetailLoadResult.Unavailable
        }

    private fun submitLeave(expectedOverlay: GroupDetailOverlay) {
        val current = _uiState.value
        if (hasPendingEmotionPresses(current)) {
            showNotice(GroupDetailNoticeKind.PressBlockedWhilePending)
            return
        }
        if (current.detail?.role == GroupRole.OWNER) {
            _uiState.update { state ->
                if (state.overlay == expectedOverlay) {
                    state.copy(overlay = GroupDetailOverlay.OwnerCannotLeave)
                } else {
                    state
                }
            }
            return
        }
        if (current.detail == null || current.overlay != expectedOverlay || leaveJob?.isActive == true) return

        invalidateLoad()
        val operationKey = dependencies.operationKeyAllocator.next()
        _uiState.update { state ->
            if (state.detail != null && state.overlay == expectedOverlay) {
                state.copy(overlay = GroupDetailOverlay.Leaving(operationKey), copyRequest = null)
            } else {
                state
            }
        }
        if ((_uiState.value.overlay as? GroupDetailOverlay.Leaving)?.operationKey != operationKey) return

        leaveJob =
            viewModelScope.launch {
                try {
                    val result =
                        try {
                            withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                                telemetry
                                    .operation(
                                        "group_leave_finished",
                                        labels(
                                            "group_operation_key" to
                                                "${operationKey.ownerInstanceId}:${operationKey.sequence}",
                                        ),
                                    ).observe(::resultLabel) { dependencies.leaveGroupAction.leave(groupId) }
                            } ?: LeaveGroupResult.OutcomeUnknown
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            dependencies.errorReporter.reportUnexpected(exception)
                            LeaveGroupResult.OutcomeUnknown
                        }

                    if ((_uiState.value.overlay as? GroupDetailOverlay.Leaving)?.operationKey == operationKey) {
                        when (result) {
                            LeaveGroupResult.Left,
                            LeaveGroupResult.MembershipChanged,
                            LeaveGroupResult.NotFound,
                            -> queuedEmotionPresses.clear()

                            else -> Unit
                        }
                    }

                    _uiState.update { state ->
                        val leaving = state.overlay as? GroupDetailOverlay.Leaving
                        if (leaving?.operationKey != operationKey) {
                            state
                        } else {
                            when (result) {
                                LeaveGroupResult.Left -> {
                                    state.copy(
                                        content = GroupDetailContent.MembershipChanged,
                                        groupName = state.detail?.group?.name ?: state.groupName,
                                        overlay = GroupDetailOverlay.Left(operationKey),
                                        membershipEvent = null,
                                        pendingEmotionPresses = emptyMap(),
                                    )
                                }

                                LeaveGroupResult.MembershipChanged -> {
                                    state.copy(
                                        content = GroupDetailContent.MembershipChanged,
                                        groupName = state.detail?.group?.name ?: state.groupName,
                                        overlay = GroupDetailOverlay.None,
                                        pendingEmotionPresses = emptyMap(),
                                        membershipEvent =
                                            GroupDetailMembershipEvent(
                                                operationKey,
                                                GroupDetailAccessLoss.MembershipChanged,
                                            ),
                                    )
                                }

                                LeaveGroupResult.NotFound -> {
                                    state.copy(
                                        content = GroupDetailContent.NotFound,
                                        groupName = state.detail?.group?.name ?: state.groupName,
                                        overlay = GroupDetailOverlay.None,
                                        pendingEmotionPresses = emptyMap(),
                                        membershipEvent =
                                            GroupDetailMembershipEvent(operationKey, GroupDetailAccessLoss.NotFound),
                                    )
                                }

                                LeaveGroupResult.OwnerCannotLeave -> {
                                    val detail = state.detail
                                    state.copy(
                                        content =
                                            detail?.let {
                                                GroupDetailContent.Ready(it.copy(role = GroupRole.OWNER))
                                            }
                                                ?: state.content,
                                        overlay = GroupDetailOverlay.OwnerCannotLeave,
                                    )
                                }

                                LeaveGroupResult.OutcomeUnknown -> {
                                    state.copy(
                                        overlay = GroupDetailOverlay.LeaveOutcomeUnknown,
                                    )
                                }

                                LeaveGroupResult.Unavailable -> {
                                    state.copy(overlay = GroupDetailOverlay.LeaveFailed)
                                }
                            }
                        }
                    }
                } finally {
                    if (leaveJob === currentCoroutineContext()[Job]) leaveJob = null
                }
            }
    }

    private fun invalidateLoad() {
        loadGeneration += 1
        loadJob?.cancel()
        loadJob = null
        _uiState.update { state -> state.copy(refreshStatus = GroupDetailRefreshStatus.Idle) }
    }

    private fun showNotice(kind: GroupDetailNoticeKind) {
        val notice = GroupDetailNotice(dependencies.operationKeyAllocator.next(), kind)
        _uiState.update { state ->
            state.copy(notice = notice)
        }
    }

    private fun showNotice(
        kind: GroupDetailNoticeKind,
        retryAfterMillis: Long?,
    ) {
        val notice = GroupDetailNotice(dependencies.operationKeyAllocator.next(), kind, retryAfterMillis)
        _uiState.update { state -> state.copy(notice = notice) }
    }

    private fun handlePressResult(
        operationKey: GroupOperationKey,
        emotion: EmotionKind,
        result: PressGroupEmotionResult,
    ) {
        val isCurrentRequest =
            (_uiState.value.pressStatus as? GroupPressStatus.Sending)?.operationKey == operationKey &&
                _uiState.value.detail
                    ?.group
                    ?.id == groupId
        if (!isCurrentRequest) return

        when (result) {
            is PressGroupEmotionResult.Pressed -> {
                _uiState.update { state ->
                    val sending = state.pressStatus as? GroupPressStatus.Sending
                    val detail = state.detail
                    if (sending?.operationKey != operationKey || detail == null || detail.group.id != groupId) {
                        state
                    } else {
                        state.copy(
                            content = GroupDetailContent.Ready(detail.withPressSnapshot(result.snapshot)),
                            pressStatus = GroupPressStatus.Idle,
                            pendingEmotionPresses = state.pendingEmotionPresses.without(emotion),
                        )
                    }
                }
            }

            PressGroupEmotionResult.MembershipChanged -> {
                queuedEmotionPresses.clear()
                markMembershipUnavailable(operationKey, GroupDetailAccessLoss.MembershipChanged)
            }

            PressGroupEmotionResult.NotFound -> {
                queuedEmotionPresses.clear()
                markMembershipUnavailable(operationKey, GroupDetailAccessLoss.NotFound)
            }

            is PressGroupEmotionResult.RateLimited -> {
                _uiState.update { state ->
                    if ((state.pressStatus as? GroupPressStatus.Sending)?.operationKey == operationKey) {
                        state.copy(
                            pressStatus = GroupPressStatus.Idle,
                            pendingEmotionPresses = state.pendingEmotionPresses.without(emotion),
                        )
                    } else {
                        state
                    }
                }
                showNotice(GroupDetailNoticeKind.PressRateLimited, result.retryAfterMillis)
            }

            PressGroupEmotionResult.Rejected -> {
                _uiState.update { state ->
                    if ((state.pressStatus as? GroupPressStatus.Sending)?.operationKey == operationKey) {
                        state.copy(
                            pressStatus = GroupPressStatus.Idle,
                            pendingEmotionPresses = state.pendingEmotionPresses.without(emotion),
                        )
                    } else {
                        state
                    }
                }
                showNotice(GroupDetailNoticeKind.PressRejected)
            }

            PressGroupEmotionResult.Unavailable -> {
                _uiState.update { state ->
                    if ((state.pressStatus as? GroupPressStatus.Sending)?.operationKey == operationKey) {
                        state.copy(
                            pressStatus = GroupPressStatus.Idle,
                            pendingEmotionPresses = state.pendingEmotionPresses.without(emotion),
                        )
                    } else {
                        state
                    }
                }
                showNotice(GroupDetailNoticeKind.PressUnavailable)
            }

            PressGroupEmotionResult.OutcomeUnknown -> {
                _uiState.update { state ->
                    if ((state.pressStatus as? GroupPressStatus.Sending)?.operationKey == operationKey) {
                        state.copy(pressStatus = GroupPressStatus.Reconciling(operationKey, emotion))
                    } else {
                        state
                    }
                }
                loadDetail(reconcilePressOperationKey = operationKey)
            }
        }
    }

    private fun markMembershipUnavailable(
        operationKey: GroupOperationKey,
        reason: GroupDetailAccessLoss,
    ) {
        val event = GroupDetailMembershipEvent(operationKey, reason)
        _uiState.update { state ->
            if ((state.pressStatus as? GroupPressStatus.Sending)?.operationKey != operationKey) {
                state
            } else {
                state.copy(
                    content =
                        if (reason == GroupDetailAccessLoss.MembershipChanged) {
                            GroupDetailContent.MembershipChanged
                        } else {
                            GroupDetailContent.NotFound
                        },
                    refreshStatus = GroupDetailRefreshStatus.Idle,
                    overlay = GroupDetailOverlay.None,
                    copyRequest = null,
                    pressStatus = GroupPressStatus.Idle,
                    pendingEmotionPresses = emptyMap(),
                    membershipEvent = event,
                )
            }
        }
    }

    private fun GroupPressStatus.afterPressReconciliation(
        operationKey: GroupOperationKey?,
        succeeded: Boolean,
    ): GroupPressStatus {
        val reconciling = this as? GroupPressStatus.Reconciling ?: return this
        if (reconciling.operationKey != operationKey) return this
        return if (succeeded) {
            GroupPressStatus.Idle
        } else {
            GroupPressStatus.OutcomeUnknown(reconciling.operationKey, reconciling.emotion)
        }
    }

    private fun GroupDetailUiModel.ownedSnapshot(): GroupDetailUiModel = copy(emotionCounts = emotionCounts.toList())
}

private fun GroupDetailUiState.withPendingPress(
    emotion: EmotionKind,
    amount: Long,
): GroupDetailUiState =
    copy(
        pendingEmotionPresses =
            pendingEmotionPresses + (emotion to ((pendingEmotionPresses[emotion] ?: 0L) + amount)),
    )

private fun Map<EmotionKind, Long>.without(emotion: EmotionKind): Map<EmotionKind, Long> {
    val count = this[emotion] ?: return this
    return if (count <= 1L) this - emotion else this + (emotion to (count - 1L))
}

private const val DEFAULT_PRESS_RETRY_DELAY_MILLIS = 1_000L

enum class GroupCopyCodeResult {
    Copied,
    Unavailable,
}
