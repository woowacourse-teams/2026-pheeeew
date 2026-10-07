package com.pheeeew.feature.screens.group.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private var loadingIndicatorDelayJob: Job? = null
    private var loadingIndicatorMinimumJob: Job? = null
    private var loadingIndicatorRequestId: Long? = null
    private var leaveJob: Job? = null
    private var loadGeneration = 0L
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
        if (_uiState.value.overlay == GroupDetailOverlay.Menu ||
            _uiState.value.overlay == GroupDetailOverlay.InviteCode
        ) {
            loadDetail(showRefreshIndicator = false)
        } else {
            onRefresh(showRefreshIndicator = false)
        }
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
        if (loadJob?.isActive == true) {
            if (showRefreshIndicator && state.detail != null) {
                _uiState.update { current ->
                    current.copy(refreshStatus = GroupDetailRefreshStatus.Refreshing, isLoading = true)
                }
                scheduleLoadingIndicator(loadGeneration)
            }
            return
        }
        loadDetail(showRefreshIndicator = showRefreshIndicator)
    }

    fun onMoreClick() {
        _uiState.update { state ->
            if (state.detail == null || state.detail?.role == GroupRole.NONE ||
                state.overlay != GroupDetailOverlay.None
            ) {
                state
            } else {
                state.copy(overlay = GroupDetailOverlay.Menu)
            }
        }
    }

    fun onInviteClick() {
        _uiState.update { state ->
            if (state.detail == null || state.detail?.role == GroupRole.NONE ||
                state.overlay != GroupDetailOverlay.None
            ) {
                state
            } else {
                state.copy(overlay = GroupDetailOverlay.InviteCode, copyRequest = null)
            }
        }
    }

    fun onLeaveMenuClick() {
        val initial = _uiState.value
        if (initial.detail?.role == GroupRole.NONE || initial.overlay != GroupDetailOverlay.Menu) return
        _uiState.update { state ->
            val detail = state.detail
            if (detail == null || detail.role == GroupRole.NONE || state.overlay != GroupDetailOverlay.Menu) {
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
        if (current.detail?.role == GroupRole.NONE || current.overlay != GroupDetailOverlay.InviteCode ||
            code == null || current.copyRequest != null
        ) {
            return
        }
        val request = GroupCopyCodeRequest(dependencies.operationKeyAllocator.next(), code)
        _uiState.update { state ->
            if (state.detail?.role == GroupRole.NONE || state.overlay != GroupDetailOverlay.InviteCode ||
                state.detail?.inviteCode != code ||
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
        val requestId = ++loadGeneration
        val hasSnapshot = _uiState.value.detail != null
        _uiState.update { state ->
            state.copy(
                content =
                    when {
                        hasSnapshot -> state.content
                        state.content == GroupDetailContent.LoadFailed -> state.content
                        else -> GroupDetailContent.Loading
                    },
                isLoading = true,
                isLoadingIndicatorVisible = false,
                refreshStatus =
                    if (hasSnapshot && showRefreshIndicator) {
                        GroupDetailRefreshStatus.Refreshing
                    } else {
                        GroupDetailRefreshStatus.Idle
                    },
            )
        }
        if (showRefreshIndicator) scheduleLoadingIndicator(requestId)

        loadJob =
            viewModelScope.launch {
                try {
                    val result =
                        telemetry
                            .operation(
                                "group_detail_load_finished",
                            ).observe(::resultLabel) { requestDetail() }
                    awaitLoadingIndicatorMinimum(requestId)
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

                    val leaveOutcomeLeftKey =
                        if (reconcileLeaveOutcome && loadedDetail?.role == GroupRole.NONE &&
                            _uiState.value.overlay == GroupDetailOverlay.LeaveOutcomeUnknown
                        ) {
                            dependencies.operationKeyAllocator.next()
                        } else {
                            null
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
                                val detail = loadedDetail
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
                                        isLoading = false,
                                        isLoadingIndicatorVisible = false,
                                    )
                                } else {
                                    state.copy(
                                        content = GroupDetailContent.Ready(detail),
                                        groupName = detail.group.name,
                                        refreshStatus = GroupDetailRefreshStatus.Idle,
                                        isLoading = false,
                                        isLoadingIndicatorVisible = false,
                                        overlay =
                                            when {
                                                reconcileLeaveOutcome &&
                                                    state.overlay == GroupDetailOverlay.LeaveOutcomeUnknown -> {
                                                    if (detail.role == GroupRole.NONE) {
                                                        GroupDetailOverlay.Left(requireNotNull(leaveOutcomeLeftKey))
                                                    } else {
                                                        GroupDetailOverlay.LeaveStillMember
                                                    }
                                                }

                                                detail.role == GroupRole.NONE &&
                                                    state.overlay in
                                                    setOf(GroupDetailOverlay.Menu, GroupDetailOverlay.InviteCode) -> {
                                                    GroupDetailOverlay.None
                                                }

                                                else -> {
                                                    state.overlay
                                                }
                                            },
                                        copyRequest = if (detail.role == GroupRole.NONE) null else state.copyRequest,
                                    )
                                }
                            }

                            GroupDetailLoadResult.MembershipChanged -> {
                                state.copy(
                                    content = GroupDetailContent.MembershipChanged,
                                    refreshStatus = GroupDetailRefreshStatus.Idle,
                                    isLoading = false,
                                    isLoadingIndicatorVisible = false,
                                    overlay = GroupDetailOverlay.None,
                                    copyRequest = null,
                                    membershipEvent = membershipEvent,
                                )
                            }

                            GroupDetailLoadResult.NotFound -> {
                                state.copy(
                                    content = GroupDetailContent.NotFound,
                                    refreshStatus = GroupDetailRefreshStatus.Idle,
                                    isLoading = false,
                                    isLoadingIndicatorVisible = false,
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
                                    isLoading = false,
                                    isLoadingIndicatorVisible = false,
                                )
                            }
                        }
                    }
                } finally {
                    if (requestId == loadGeneration) {
                        clearLoadingIndicator(requestId)
                        _uiState.update { state ->
                            if (state.isLoading) {
                                state.copy(
                                    isLoading = false,
                                    isLoadingIndicatorVisible = false,
                                    refreshStatus =
                                        if (state.refreshStatus == GroupDetailRefreshStatus.Refreshing) {
                                            GroupDetailRefreshStatus.Idle
                                        } else {
                                            state.refreshStatus
                                        },
                                )
                            } else {
                                state
                            }
                        }
                        loadJob = null
                    }
                }
            }
    }

    private fun scheduleLoadingIndicator(requestId: Long) {
        if (loadingIndicatorRequestId == requestId) return
        loadingIndicatorRequestId = requestId
        loadingIndicatorDelayJob =
            viewModelScope.launch {
                delay(LOADING_INDICATOR_DELAY_MILLIS)
                if (requestId != loadGeneration || !_uiState.value.isLoading) return@launch
                _uiState.update { state ->
                    if (state.isLoading) state.copy(isLoadingIndicatorVisible = true) else state
                }
                loadingIndicatorMinimumJob =
                    viewModelScope.launch {
                        delay(LOADING_INDICATOR_MINIMUM_MILLIS)
                    }
            }
    }

    private suspend fun awaitLoadingIndicatorMinimum(requestId: Long) {
        if (loadingIndicatorRequestId != requestId) return
        loadingIndicatorDelayJob?.cancelAndJoin()
        if (_uiState.value.isLoadingIndicatorVisible) loadingIndicatorMinimumJob?.join()
    }

    private fun clearLoadingIndicator(requestId: Long? = null) {
        if (requestId != null && loadingIndicatorRequestId != requestId) return
        loadingIndicatorDelayJob?.cancel()
        loadingIndicatorMinimumJob?.cancel()
        loadingIndicatorDelayJob = null
        loadingIndicatorMinimumJob = null
        loadingIndicatorRequestId = null
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
        if (current.detail?.role == GroupRole.NONE) {
            _uiState.update { state ->
                if (state.overlay == expectedOverlay) state.copy(overlay = GroupDetailOverlay.None) else state
            }
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
            if (state.detail?.role != GroupRole.NONE && state.detail != null && state.overlay == expectedOverlay) {
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
        clearLoadingIndicator()
        loadJob?.cancel()
        loadJob = null
        _uiState.update { state ->
            state.copy(
                refreshStatus = GroupDetailRefreshStatus.Idle,
                isLoading = false,
                isLoadingIndicatorVisible = false,
            )
        }
    }

    private fun showNotice(kind: GroupDetailNoticeKind) {
        val notice = GroupDetailNotice(dependencies.operationKeyAllocator.next(), kind)
        _uiState.update { state ->
            state.copy(notice = notice)
        }
    }
}

enum class GroupCopyCodeResult {
    Copied,
    Unavailable,
}

private const val LOADING_INDICATOR_DELAY_MILLIS = 150L
private const val LOADING_INDICATOR_MINIMUM_MILLIS = 300L
