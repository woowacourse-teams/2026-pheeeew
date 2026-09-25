package com.pheeeew.feature.screens.group.join

import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 홈 ViewModel이 소유하는 코드 조회와 참여 요청의 상태·수명 관리자입니다. */
class GroupJoinStateHolder(
    private val scope: CoroutineScope,
    private val dependencies: GroupJoinDependencies,
) {
    private val _uiState = MutableStateFlow(GroupJoinUiState())
    val uiState = _uiState.asStateFlow()

    private var isOpen = false
    private var requestGeneration = 0L
    private var lookupJob: Job? = null
    private var joinJob: Job? = null

    fun open() {
        if (isOpen) return
        isOpen = true
        _uiState.value = GroupJoinUiState()
    }

    /** 처리 중에는 화면 닫기를 거부하고, 조회 중에는 해당 요청도 취소합니다. */
    fun close(): Boolean {
        if (!isOpen) return true
        if (_uiState.value.isDismissBlocked) return false

        invalidateRequests()
        isOpen = false
        _uiState.value = GroupJoinUiState()
        return true
    }

    fun onCodeChanged(value: String) {
        if (!isOpen || _uiState.value.isInteractionLocked) return

        invalidateRequests()
        _uiState.update { state ->
            state.copy(
                input = value,
                hasAttemptedSearch = false,
                lookup = GroupLookupState.Idle,
                submission = GroupJoinSubmissionState.Idle,
            )
        }
    }

    fun onSearchClick() {
        val current = _uiState.value
        if (!isOpen || current.isInteractionLocked || current.isLookingUp) return

        val code =
            when (val validation = GroupCodeRules.validate(current.input)) {
                GroupCodeValidation.Empty -> {
                    return
                }

                is GroupCodeValidation.Valid -> {
                    validation.normalizedCode
                }

                is GroupCodeValidation.TooLong,
                is GroupCodeValidation.TooShort,
                GroupCodeValidation.InvalidCharacters,
                -> {
                    _uiState.value = current.copy(hasAttemptedSearch = true)
                    return
                }
            }

        val requestId = nextRequestGeneration()
        lookupJob?.cancel()
        _uiState.value =
            current.copy(
                hasAttemptedSearch = true,
                lookup = GroupLookupState.Loading(requestId, code),
                submission = GroupJoinSubmissionState.Idle,
            )

        lookupJob =
            scope.launch {
                val result = lookup(code)
                if (!isCurrentLookup(requestId, code)) return@launch

                _uiState.update { state ->
                    if (!isActiveLookup(state, requestId, code)) return@update state
                    state.copy(
                        lookup =
                            when (result) {
                                is GroupLookupResult.Found -> GroupLookupState.Found(code, result.group)
                                GroupLookupResult.NotFound -> GroupLookupState.NotFound(code)
                                GroupLookupResult.Unavailable -> GroupLookupState.Failed(code)
                            },
                    )
                }
                if (requestId == requestGeneration) lookupJob = null
            }
    }

    fun onJoinClick() {
        val current = _uiState.value
        if (!isOpen || !current.canJoin) return

        val found = current.lookup as? GroupLookupState.Found ?: return
        val operationKey = dependencies.operationKeyAllocator.next()
        val requestId = nextRequestGeneration()
        _uiState.value =
            current.copy(
                submission = GroupJoinSubmissionState.Submitting(operationKey, found.group.id),
            )

        joinJob =
            scope.launch {
                val result = join(found.group.id, found.requestedCode)
                if (!isCurrentJoin(requestId, operationKey)) return@launch

                _uiState.update { state ->
                    val submitting = state.submission as? GroupJoinSubmissionState.Submitting
                    if (submitting?.operationKey != operationKey) return@update state
                    state.copy(
                        submission =
                            when (result) {
                                is GroupJoinResult.Joined -> {
                                    GroupJoinSubmissionState.Succeeded(operationKey, result.groupId)
                                }

                                GroupJoinResult.Rejected -> {
                                    GroupJoinSubmissionState.Failed(GroupJoinFailure.Rejected)
                                }

                                GroupJoinResult.Unavailable -> {
                                    GroupJoinSubmissionState.Failed(GroupJoinFailure.Unavailable)
                                }

                                GroupJoinResult.OutcomeUnknown -> {
                                    GroupJoinSubmissionState.Failed(GroupJoinFailure.OutcomeUnknown)
                                }
                            },
                    )
                }
                if (requestId == requestGeneration) joinJob = null
            }
    }

    /** Route가 성공 이동을 처리한 경우에만 같은 키의 결과를 소비하고 시트를 초기화합니다. */
    fun consumeAndClose(operationKey: GroupOperationKey): Boolean {
        val succeeded = _uiState.value.submission as? GroupJoinSubmissionState.Succeeded ?: return false
        if (!isOpen || succeeded.operationKey != operationKey) return false

        invalidateRequests()
        isOpen = false
        _uiState.value = GroupJoinUiState()
        return true
    }

    private suspend fun lookup(code: String): GroupLookupResult =
        try {
            withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                dependencies.lookupGroupAction.find(code)
            } ?: GroupLookupResult.Unavailable
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            dependencies.errorReporter.reportUnexpected(exception)
            GroupLookupResult.Unavailable
        }

    private suspend fun join(
        groupId: GroupId,
        code: String,
    ): GroupJoinResult =
        try {
            withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                dependencies.joinGroupAction.join(groupId, code)
            } ?: GroupJoinResult.OutcomeUnknown
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            dependencies.errorReporter.reportUnexpected(exception)
            GroupJoinResult.OutcomeUnknown
        }

    private fun isCurrentLookup(
        requestId: Long,
        code: String,
    ): Boolean = isOpen && requestId == requestGeneration && GroupCodeRules.normalize(_uiState.value.input) == code

    private fun isActiveLookup(
        state: GroupJoinUiState,
        requestId: Long,
        code: String,
    ): Boolean {
        val loading = state.lookup as? GroupLookupState.Loading
        return loading?.requestId == requestId && loading.requestedCode == code
    }

    private fun isCurrentJoin(
        requestId: Long,
        operationKey: GroupOperationKey,
    ): Boolean {
        val submitting = _uiState.value.submission as? GroupJoinSubmissionState.Submitting
        return isOpen && requestId == requestGeneration && submitting?.operationKey == operationKey
    }

    private fun invalidateRequests() {
        nextRequestGeneration()
        lookupJob?.cancel()
        lookupJob = null
        joinJob?.cancel()
        joinJob = null
    }

    private fun nextRequestGeneration(): Long {
        check(requestGeneration < Long.MAX_VALUE) { "더 이상 요청을 시작할 수 없습니다." }
        requestGeneration += 1L
        return requestGeneration
    }
}
