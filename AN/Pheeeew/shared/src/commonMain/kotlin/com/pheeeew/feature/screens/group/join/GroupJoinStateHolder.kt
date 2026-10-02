package com.pheeeew.feature.screens.group.join

import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val telemetry = ProductMonitoring(dependencies.monitoring, "group_home")
    private val _uiState = MutableStateFlow(GroupJoinUiState())
    val uiState = _uiState.asStateFlow()

    private var isOpen = false
    private var requestGeneration = 0L
    private var lookupJob: Job? = null
    private var joinJob: Job? = null
    private var rateLimitJob: Job? = null
    private var rateLimitGeneration = 0L
    private var activeRateLimit: GroupJoinRateLimit? = null

    fun open() {
        if (isOpen) return
        telemetry.emit("group_join_started")
        isOpen = true
        _uiState.value = GroupJoinUiState(rateLimit = activeRateLimit)
    }

    /** 처리 중에는 화면 닫기를 거부하고, 조회 중에는 해당 요청도 취소합니다. */
    fun close(): Boolean {
        if (!isOpen) return true
        if (_uiState.value.isDismissBlocked) return false
        telemetry.emit("group_flow_closed", labels("operation_kind" to "join"))

        invalidateRequests()
        isOpen = false
        _uiState.value = GroupJoinUiState(rateLimit = activeRateLimit)
        return true
    }

    fun onCodeChanged(value: String) {
        if (!isOpen || _uiState.value.isInteractionLocked) return

        invalidateRequests()
        _uiState.update { state ->
            state.copy(
                input = GroupCodeRules.normalize(value).take(GROUP_INVITATION_CODE_LENGTH),
                hasAttemptedSearch = false,
                lookup = GroupLookupState.Idle,
                submission = GroupJoinSubmissionState.Idle,
            )
        }
    }

    fun onSearchClick() {
        val current = _uiState.value
        if (!isOpen || !current.canSearch) return

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
                val result = telemetry.operation("group_lookup_finished").observe(::resultLabel) { lookup(code) }
                if (!isCurrentLookup(requestId, code)) return@launch

                _uiState.update { state ->
                    if (!isActiveLookup(state, requestId, code)) return@update state
                    state.copy(
                        lookup =
                            when (result) {
                                is GroupLookupResult.Found -> GroupLookupState.Found(code, result.group)
                                GroupLookupResult.NotFound -> GroupLookupState.NotFound(code)
                                is GroupLookupResult.RateLimited -> GroupLookupState.RateLimited(code)
                                GroupLookupResult.Unavailable -> GroupLookupState.Failed(code)
                            },
                    )
                }
                (result as? GroupLookupResult.RateLimited)?.let { rateLimited ->
                    startRateLimitWindow(GroupJoinRateLimitOperation.Lookup, rateLimited.retryAfterMillis)
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
                val result =
                    telemetry
                        .operation(
                            "group_join_finished",
                            labels("group_key" to found.group.id.value),
                            started = "group_join_submit_started",
                        ).observe(::resultLabel) {
                            join(found.group.id, found.requestedCode)
                        }
                if (!isCurrentJoin(requestId, operationKey)) return@launch

                _uiState.update { state ->
                    val submitting = state.submission as? GroupJoinSubmissionState.Submitting
                    if (submitting?.operationKey != operationKey) return@update state
                    when (result) {
                        is GroupJoinResult.Joined -> {
                            state.copy(
                                submission = GroupJoinSubmissionState.Succeeded(operationKey, result.groupId),
                            )
                        }

                        GroupJoinResult.InviteCodeNotFound -> {
                            state.copy(
                                lookup = GroupLookupState.NotFound(found.requestedCode),
                                submission = GroupJoinSubmissionState.Idle,
                            )
                        }

                        GroupJoinResult.AlreadyMember -> {
                            state.copy(submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.AlreadyMember))
                        }

                        is GroupJoinResult.RateLimited -> {
                            state.copy(submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.RateLimited))
                        }

                        GroupJoinResult.Rejected -> {
                            state.copy(submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.Rejected))
                        }

                        GroupJoinResult.Unavailable -> {
                            state.copy(submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.Unavailable))
                        }

                        GroupJoinResult.OutcomeUnknown -> {
                            state.copy(submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.OutcomeUnknown))
                        }
                    }
                }
                (result as? GroupJoinResult.RateLimited)?.let { rateLimited ->
                    startRateLimitWindow(GroupJoinRateLimitOperation.Join, rateLimited.retryAfterMillis)
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
        _uiState.value = GroupJoinUiState(rateLimit = activeRateLimit)
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

    private fun startRateLimitWindow(
        operation: GroupJoinRateLimitOperation,
        retryAfterMillis: Long?,
    ) {
        val delayMillis = retryAfterMillis?.takeIf { it > 0L } ?: return
        check(rateLimitGeneration < Long.MAX_VALUE) { "요청 제한 세대를 더 이상 올릴 수 없습니다." }
        rateLimitGeneration += 1L
        val generation = rateLimitGeneration
        val rateLimit = GroupJoinRateLimit(operation)
        activeRateLimit = rateLimit
        rateLimitJob?.cancel()
        _uiState.update { state -> state.copy(rateLimit = rateLimit) }
        rateLimitJob =
            scope.launch {
                delay(delayMillis)
                if (generation != rateLimitGeneration) return@launch
                activeRateLimit = null
                _uiState.update { state ->
                    if (state.rateLimit != rateLimit) {
                        state
                    } else {
                        state.copy(
                            lookup =
                                if (operation == GroupJoinRateLimitOperation.Lookup &&
                                    state.lookup is GroupLookupState.RateLimited
                                ) {
                                    GroupLookupState.Idle
                                } else {
                                    state.lookup
                                },
                            submission =
                                if (operation == GroupJoinRateLimitOperation.Join &&
                                    (state.submission as? GroupJoinSubmissionState.Failed)?.reason ==
                                    GroupJoinFailure.RateLimited
                                ) {
                                    GroupJoinSubmissionState.Idle
                                } else {
                                    state.submission
                                },
                            rateLimit = null,
                        )
                    }
                }
                if (generation == rateLimitGeneration) rateLimitJob = null
            }
    }

    private fun nextRequestGeneration(): Long {
        check(requestGeneration < Long.MAX_VALUE) { "더 이상 요청을 시작할 수 없습니다." }
        requestGeneration += 1L
        return requestGeneration
    }
}
