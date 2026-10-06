package com.pheeeew.feature.screens.group.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    pressWorkOwner: GroupEmotionPressWorkOwner? = null,
) : ViewModel() {
    val telemetry = ProductMonitoring(dependencies.monitoring, "group_detail", labels("group_key" to groupId.value))
    private val ownsPressWorkOwner = pressWorkOwner == null
    private val workOwner = pressWorkOwner ?: GroupEmotionPressWorkOwner()
    private val pressSession = workOwner.session(groupId, dependencies)
    private val _uiState =
        MutableStateFlow(
            GroupDetailUiState(
                groupName = initialGroupName,
                content = pressSession.latestDetail?.let(GroupDetailContent::Ready) ?: GroupDetailContent.Loading,
            ),
        )
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var leaveJob: Job? = null
    private val emotionRankingStateHolder =
        GroupDetailEmotionRankingStateHolder(
            groupId = groupId,
            source = dependencies.emotionRankingSource,
            scope = viewModelScope,
        ) { ranking ->
            _uiState.update { it.copy(emotionRanking = ranking) }
        }
    var lastAcceptedPressKey: GroupOperationKey? = null
        private set
    private var loadGeneration = 0L
    private var hasResumed = false
    private var needsInitialLoad = true
    private var hasAttachedPressObserver = false
    private val pressObserver =
        object : GroupEmotionPressSession.Observer {
            override fun onStateChanged(
                status: GroupPressStatus,
                pending: Map<EmotionKind, Long>,
                unconfirmed: List<GroupUnconfirmedPress>,
                canAcceptAnotherPress: Boolean,
            ) {
                _uiState.update {
                    it.copy(
                        pressStatus = status,
                        pendingEmotionPresses = pending,
                        unconfirmedEmotionPresses = unconfirmed,
                        canAcceptEmotionPress = canAcceptAnotherPress,
                    )
                }
                requestDeferredInitialLoad()
            }

            override fun onBeforeSend() = invalidateLoad()

            override fun onSnapshot(
                snapshot: GroupPressSnapshotUiModel,
                pending: Map<EmotionKind, Long>,
            ) {
                _uiState.update { state ->
                    val detail = state.detail
                    if (detail?.group?.id == groupId) {
                        state.copy(
                            content = GroupDetailContent.Ready(detail.withPressSnapshot(snapshot)),
                            pressStatus = GroupPressStatus.Idle,
                            pendingEmotionPresses = pending,
                        )
                    } else {
                        state
                    }
                }
                requestDeferredInitialLoad()
            }

            override fun onReconciledDetail(detail: GroupDetailUiModel) {
                needsInitialLoad = false
                _uiState.update { state ->
                    state.copy(
                        content =
                            GroupDetailContent.Ready(
                                detail.ownedSnapshot(),
                            ),
                        groupName = detail.group.name,
                        refreshStatus = GroupDetailRefreshStatus.Idle,
                    )
                }
            }

            override fun onAccessLost(
                key: GroupOperationKey,
                reason: GroupDetailAccessLoss,
            ) {
                markMembershipUnavailable(key, reason)
            }

            override fun onNotice(
                kind: GroupDetailNoticeKind,
                retryAfterMillis: Long?,
            ) {
                showNotice(kind, retryAfterMillis)
            }
        }

    init {
        pressSession.attach(pressObserver)
        hasAttachedPressObserver = true
        loadDetail()
    }

    override fun onCleared() {
        hasAttachedPressObserver = false
        pressSession.detach(pressObserver)
        emotionRankingStateHolder.clear()
        if (ownsPressWorkOwner) pressSession.close()
        super.onCleared()
    }

    private fun requestDeferredInitialLoad() {
        if (!needsInitialLoad || !hasAttachedPressObserver) return
        viewModelScope.launch {
            if (needsInitialLoad && !pressSession.accessLost && !pressSession.hasActiveWork &&
                pressSession.status == GroupPressStatus.Idle
            ) {
                loadDetail()
            }
        }
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

    fun onRetryEmotionRanking() {
        if (_uiState.value.detail == null) return
        emotionRankingStateHolder.load()
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
            is GroupPressStatus.CoolingDown,
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
        if (loadJob?.isActive == true) {
            if (showRefreshIndicator && state.detail != null) {
                _uiState.update { current -> current.copy(refreshStatus = GroupDetailRefreshStatus.Refreshing) }
            }
            return
        }
        loadDetail(showRefreshIndicator = showRefreshIndicator)
    }

    /** Accepts taps while a press request is in flight and submits them in order. */
    fun onEmotionTap(emotion: EmotionKind): Boolean {
        lastAcceptedPressKey = null
        val current = _uiState.value
        if (current.detail?.group?.id != groupId || current.overlay != GroupDetailOverlay.None) return false
        lastAcceptedPressKey = pressSession.accept(emotion)
        return lastAcceptedPressKey != null
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
        if (pressSession.hasActiveWork) {
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

    fun onInviteShareUnavailable() {
        showNotice(GroupDetailNoticeKind.InviteShareUnavailable)
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
        if (loadJob?.isActive == true) return
        pressSession.resolveUnknown()
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
        showRefreshIndicator: Boolean = true,
    ) {
        if (loadJob?.isActive == true) return
        val currentPressStatus = _uiState.value.pressStatus
        if (currentPressStatus is GroupPressStatus.OutcomeUnknown) {
            pressSession.resolveUnknown()
            return
        }
        if (pressSession.hasActiveWork || currentPressStatus is GroupPressStatus.Sending ||
            currentPressStatus is GroupPressStatus.Reconciling
        ) {
            return
        }

        needsInitialLoad = false
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
                    if (reconcileLeaveOutcome) {
                        telemetry.emit(
                            "operation_reconciled",
                            labels(
                                "operation_kind" to "group_leave",
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
                    val loadedDetail =
                        (result as? GroupDetailLoadResult.Loaded)
                            ?.detail
                            ?.takeIf { it.group.id == groupId }
                            ?.let(pressSession::onDetailLoaded)

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
                                val detail = loadedDetail?.ownedSnapshot()
                                if (detail == null) {
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
                                    )
                                } else {
                                    state.copy(
                                        content = GroupDetailContent.Ready(detail),
                                        groupName = detail.group.name,
                                        refreshStatus = GroupDetailRefreshStatus.Idle,
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
                                    membershipEvent = membershipEvent,
                                )
                            }

                            GroupDetailLoadResult.NotFound -> {
                                state.copy(
                                    content = GroupDetailContent.NotFound,
                                    refreshStatus = GroupDetailRefreshStatus.Idle,
                                    overlay = GroupDetailOverlay.None,
                                    copyRequest = null,
                                    membershipEvent = membershipEvent,
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
                                )
                            }
                        }
                    }
                    if (loadedDetail != null) emotionRankingStateHolder.load(force = true)
                    if (result == GroupDetailLoadResult.MembershipChanged || result == GroupDetailLoadResult.NotFound) {
                        pressSession.clearForAccessLoss()
                    }
                } finally {
                    if (requestId == loadGeneration) {
                        loadJob = null
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
        if (pressSession.hasActiveWork) {
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
                            -> pressSession.clearForAccessLoss()

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
                                    )
                                }

                                LeaveGroupResult.MembershipChanged -> {
                                    state.copy(
                                        content = GroupDetailContent.MembershipChanged,
                                        groupName = state.detail?.group?.name ?: state.groupName,
                                        overlay = GroupDetailOverlay.None,
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

    private fun markMembershipUnavailable(
        operationKey: GroupOperationKey,
        reason: GroupDetailAccessLoss,
    ) {
        invalidateLoad()
        val event = GroupDetailMembershipEvent(operationKey, reason)
        _uiState.update { state ->
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
                membershipEvent = event,
            )
        }
    }

    private fun GroupDetailUiModel.ownedSnapshot(): GroupDetailUiModel = copy(emotionCounts = emotionCounts.toList())
}

enum class GroupCopyCodeResult {
    Copied,
    Unavailable,
}
