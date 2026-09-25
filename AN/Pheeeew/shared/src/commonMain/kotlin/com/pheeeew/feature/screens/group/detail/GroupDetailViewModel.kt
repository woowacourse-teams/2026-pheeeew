package com.pheeeew.feature.screens.group.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupDetailUiState(groupName = initialGroupName))
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var leaveJob: Job? = null
    private var loadGeneration = 0L

    init {
        loadDetail()
    }

    fun onRetry() {
        val state = _uiState.value
        if (state.overlay != GroupDetailOverlay.None || state.content is GroupDetailContent.MembershipChanged) return
        loadDetail()
    }

    fun onRefresh() {
        if (_uiState.value.overlay != GroupDetailOverlay.None) return
        loadDetail()
    }

    /** Fixture 스냅샷을 즉시 적용합니다. 실제 요청 입력 큐로 쓰지 않습니다. */
    fun onEmotionTap(emotion: EmotionKind): Boolean {
        val current = _uiState.value
        val currentDetail = current.detail ?: return false
        if (!current.canTapEmotion) return false

        invalidateLoad()
        val result =
            try {
                dependencies.emotionTapAction.apply(groupId, emotion)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                dependencies.errorReporter.reportUnexpected(exception)
                EmotionTapResult.Unavailable
            }

        when (result) {
            is EmotionTapResult.Applied -> {
                val detail = result.detail.ownedSnapshot()
                if (detail.group.id != groupId || detail.group.id != currentDetail.group.id) {
                    dependencies.errorReporter.reportUnexpected(
                        IllegalStateException("감정 fixture가 현재 그룹과 다른 상세를 반환했습니다."),
                    )
                    showNotice(GroupDetailNoticeKind.EmotionUnavailable)
                    return false
                }
                _uiState.update { state ->
                    if (state.canTapEmotion && state.detail?.group?.id == groupId) {
                        state.copy(content = GroupDetailContent.Ready(detail))
                    } else {
                        state
                    }
                }
                return _uiState.value.detail == detail
            }

            EmotionTapResult.Unavailable -> {
                showNotice(GroupDetailNoticeKind.EmotionUnavailable)
                return false
            }
        }
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
        _uiState.update { state ->
            if (state.detail == null || state.overlay != GroupDetailOverlay.Menu) {
                state
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
                -> {
                    state.copy(overlay = GroupDetailOverlay.None, copyRequest = null)
                }

                GroupDetailOverlay.None,
                GroupDetailOverlay.LeaveOutcomeUnknown,
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
            -> {
                onDismissOverlay()
                false
            }

            is GroupDetailOverlay.Leaving,
            GroupDetailOverlay.LeaveOutcomeUnknown,
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
        submitLeave(expectedOverlay = GroupDetailOverlay.LeaveFailed)
    }

    /** 결과가 불명확할 때 쓰기 요청을 재전송하지 않고 상세/멤버십을 다시 조회합니다. */
    fun onResolveLeaveOutcome() {
        if (_uiState.value.overlay != GroupDetailOverlay.LeaveOutcomeUnknown) return
        _uiState.update { state ->
            if (state.overlay == GroupDetailOverlay.LeaveOutcomeUnknown) {
                state.copy(overlay = GroupDetailOverlay.None)
            } else {
                state
            }
        }
        loadDetail()
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

    private fun loadDetail() {
        if (loadJob?.isActive == true) return

        val requestId = ++loadGeneration
        val hasSnapshot = _uiState.value.detail != null
        _uiState.update { state ->
            state.copy(
                content = if (hasSnapshot) state.content else GroupDetailContent.Loading,
                refreshStatus = if (hasSnapshot) GroupDetailRefreshStatus.Refreshing else GroupDetailRefreshStatus.Idle,
            )
        }

        loadJob =
            viewModelScope.launch {
                try {
                    val result = requestDetail()
                    if (requestId != loadGeneration) return@launch

                    if (result is GroupDetailLoadResult.Loaded && result.detail.group.id != groupId) {
                        dependencies.errorReporter.reportUnexpected(
                            IllegalStateException("상세 공급자가 요청한 그룹과 다른 ID를 반환했습니다."),
                        )
                    }

                    _uiState.update { state ->
                        when (result) {
                            is GroupDetailLoadResult.Loaded -> {
                                val detail = result.detail.ownedSnapshot()
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
                                    )
                                } else {
                                    state.copy(
                                        content = GroupDetailContent.Ready(detail),
                                        groupName = detail.group.name,
                                        refreshStatus = GroupDetailRefreshStatus.Idle,
                                    )
                                }
                            }

                            GroupDetailLoadResult.MembershipChanged -> {
                                state.copy(
                                    content = GroupDetailContent.MembershipChanged,
                                    refreshStatus = GroupDetailRefreshStatus.Idle,
                                    overlay = GroupDetailOverlay.None,
                                    copyRequest = null,
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
                } finally {
                    if (requestId == loadGeneration) loadJob = null
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
                                dependencies.leaveGroupAction.leave(groupId)
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

    private fun GroupDetailUiModel.ownedSnapshot(): GroupDetailUiModel = copy(emotionCounts = emotionCounts.toList())
}

enum class GroupCopyCodeResult {
    Copied,
    Unavailable,
}
