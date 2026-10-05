package com.pheeeew.feature.screens.group.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.create.model.StampTextColorOption
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/** 생성 초안, 검증, 확인, 제출 및 결과 전달 수명을 소유합니다. */
class GroupCreateViewModel(
    private val createGroupAction: CreateGroupAction,
    private val errorReporter: GroupCreateErrorReporter,
    private val operationKeyAllocator: GroupOperationKeyAllocator,
    val formRules: GroupFormRules = GroupFormRules(),
    private val requestPolicy: CreateRequestPolicy = CreateRequestPolicy(),
    private val findCandidatesAction: FindGroupCreateCandidatesAction =
        FindGroupCreateCandidatesAction { GroupCreateCandidatesResult.Unavailable },
    private val sessionStore: GroupCreateSessionStore = EmptyGroupCreateSessionStore,
    monitoring: com.pheeeew.core.monitoring.Monitoring = com.pheeeew.core.monitoring.NoOpMonitoring,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    val telemetry = ProductMonitoring(monitoring, "group_create")
    private val _uiState = MutableStateFlow(GroupCreateUiState(restoration = GroupCreateRestorationState.Restoring))
    val uiState = _uiState.asStateFlow()
    private val sessionWriteMutex = Mutex()
    private var pendingOperation: PersistedGroupCreateOperation? = null
    private var draftUpdatedAtEpochMillis: Long? = null
    private var lastAppliedHue: Float? = null

    init {
        restoreSession()
    }

    fun onNameChanged(value: String) {
        updateDraft(
            transform = { draft -> draft.copy(name = formRules.limitGroupName(value)) },
            clearError = { errors -> errors.copy(name = null) },
        )
    }

    fun onDescriptionChanged(value: String) {
        updateDraft(
            transform = { draft -> draft.copy(description = formRules.limitDescription(value)) },
            clearError = { errors -> errors.copy(description = null) },
        )
    }

    fun onStampLabelChanged(value: String) {
        updateDraft(
            transform = { draft ->
                draft.copy(stamp = draft.stamp.copy(label = formRules.limitStampLabel(value)))
            },
            clearError = { errors -> errors.copy(stampLabel = null) },
        )
    }

    fun onStampShapeChanged(shape: StampShapeId) {
        updateDraft { draft -> draft.copy(stamp = draft.stamp.copy(shape = shape)) }
    }

    fun onStampTextColorChanged(color: StampTextColorOption) {
        updateDraft { draft -> draft.copy(stamp = draft.stamp.copy(textArgb = color.argb)) }
    }

    fun onCreateClick() {
        val current = _uiState.value
        if (current.restoration != GroupCreateRestorationState.Ready || pendingOperation != null ||
            current.submission != GroupCreateSubmissionState.Editing ||
            current.colorSheet !is StampColorSheetState.Closed
        ) {
            return
        }

        val normalizedDraft = current.draft.normalizedForSubmission()
        val errors = formRules.validate(normalizedDraft)
        if (errors.hasErrors) {
            listOf(
                "name" to errors.name,
                "description" to errors.description,
                "stamp_label" to errors.stampLabel,
            ).forEach { (field, rule) ->
                if (rule !=
                    null
                ) {
                    telemetry.emit(
                        "group_create_validation_failed",
                        labels(
                            "field" to field,
                            "rule" to rule.name.lowercase(),
                        ),
                    )
                }
            }
            _uiState.update { state ->
                if (state.submission == GroupCreateSubmissionState.Editing &&
                    state.colorSheet is StampColorSheetState.Closed
                ) {
                    state.copy(fieldErrors = errors)
                } else {
                    state
                }
            }
        } else {
            _uiState.update { state ->
                if (state.submission == GroupCreateSubmissionState.Editing &&
                    state.colorSheet is StampColorSheetState.Closed
                ) {
                    state.copy(
                        submission = GroupCreateSubmissionState.Confirming,
                        fieldErrors = GroupCreateFieldErrors(),
                    )
                } else {
                    state
                }
            }
        }
    }

    fun onCancelConfirmation() {
        if (_uiState.value.submission == GroupCreateSubmissionState.Confirming) {
            telemetry.emit("group_create_confirmation_resolved", labels("action" to "cancel"))
        }
        _uiState.update { state ->
            if (state.submission == GroupCreateSubmissionState.Confirming) {
                state.copy(submission = GroupCreateSubmissionState.Editing)
            } else {
                state
            }
        }
    }

    fun onConfirmCreate() {
        val current = _uiState.value
        if (current.restoration != GroupCreateRestorationState.Ready || pendingOperation != null ||
            current.submission != GroupCreateSubmissionState.Confirming ||
            current.colorSheet !is StampColorSheetState.Closed
        ) {
            return
        }

        telemetry.emit("group_create_confirmation_resolved", labels("action" to "confirm"))
        submitDraft(
            draft = current.draft.normalizedForSubmission(),
            expectedSubmission = GroupCreateSubmissionState.Confirming,
        )
    }

    /** 실패 팝업의 명시적인 재시도 동작입니다. 자동 재시도는 하지 않습니다. */
    fun onRetryFailure() {
        val current = _uiState.value
        val failed = current.submission as? GroupCreateSubmissionState.Failed ?: return
        if (pendingOperation != null || failed.reason == GroupCreateFailure.OutcomeUnknown ||
            failed.reason == GroupCreateFailure.RateLimited
        ) {
            return
        }
        if (current.colorSheet !is StampColorSheetState.Closed) return

        submitDraft(
            draft = current.draft.normalizedForSubmission(),
            expectedSubmission = failed,
        )
    }

    /** Reads current memberships before offering any retry for an uncertain create. */
    fun onCheckGroupsAfterUnknownOutcome() {
        val current = _uiState.value
        val failed = current.submission as? GroupCreateSubmissionState.Failed ?: return
        val operation = pendingOperation ?: return
        if (failed.reason != GroupCreateFailure.OutcomeUnknown || failed.operationKey == null) return
        if (current.recovery == GroupCreateRecoveryState.Checking) return

        _uiState.update { state ->
            if (state.submission == failed) state.copy(recovery = GroupCreateRecoveryState.Checking) else state
        }
        if (_uiState.value.recovery != GroupCreateRecoveryState.Checking) return

        viewModelScope.launch {
            val result =
                try {
                    telemetry
                        .operation(
                            "operation_reconciled",
                            labels("operation_kind" to "group_create_candidates"),
                        ).observe(::resultLabel) {
                            findCandidatesAction.findCandidates(operation.draft.name.trim())
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    errorReporter.reportUnexpected(exception)
                    GroupCreateCandidatesResult.Unavailable
                }

            _uiState.update { state ->
                if (state.submission != failed || state.recovery != GroupCreateRecoveryState.Checking) {
                    return@update state
                }
                state.copy(
                    recovery =
                        when (result) {
                            is GroupCreateCandidatesResult.Loaded -> {
                                GroupCreateRecoveryState.Loaded(result.candidates.distinctBy { it.groupId })
                            }

                            GroupCreateCandidatesResult.Unavailable -> {
                                GroupCreateRecoveryState.Unavailable
                            }
                        },
                )
            }
        }
    }

    /** A user-selected candidate becomes the completion target; its ID is never inferred. */
    fun onSelectRecoveryCandidate(groupId: GroupId) {
        _uiState.update { state ->
            val failed = state.submission as? GroupCreateSubmissionState.Failed ?: return@update state
            if (failed.reason != GroupCreateFailure.OutcomeUnknown) return@update state
            val operationKey = failed.operationKey ?: return@update state
            val candidates = (state.recovery as? GroupCreateRecoveryState.Loaded)?.candidates ?: return@update state
            val candidate = candidates.firstOrNull { it.groupId == groupId } ?: return@update state
            state.copy(
                submission = GroupCreateSubmissionState.Succeeded(operationKey, candidate.groupId),
                recovery = GroupCreateRecoveryState.Idle,
                isRecoveryDialogVisible = false,
            )
        }
        persistCurrentSession()
    }

    fun onShowRecoveryDialog() {
        if (pendingOperation == null) return
        val shouldCheck =
            _uiState.value.recovery == GroupCreateRecoveryState.Idle ||
                _uiState.value.recovery == GroupCreateRecoveryState.Unavailable
        _uiState.update { state ->
            if ((state.submission as? GroupCreateSubmissionState.Failed)?.reason == GroupCreateFailure.OutcomeUnknown) {
                state.copy(isRecoveryDialogVisible = true)
            } else {
                state
            }
        }
        if (shouldCheck) onCheckGroupsAfterUnknownOutcome()
    }

    /** A deliberate second POST is available only after a successful membership re-read. */
    fun onRetryUnknownCreation() {
        val current = _uiState.value
        val failed = current.submission as? GroupCreateSubmissionState.Failed ?: return
        val operation = pendingOperation ?: return
        if (failed.reason != GroupCreateFailure.OutcomeUnknown ||
            current.recovery !is GroupCreateRecoveryState.Loaded ||
            current.recovery.candidates.isNotEmpty() ||
            current.colorSheet !is StampColorSheetState.Closed
        ) {
            return
        }
        submitDraft(
            draft = operation.draft.toDraft(),
            expectedSubmission = failed,
        )
    }

    private fun submitDraft(
        draft: GroupCreateDraft,
        expectedSubmission: GroupCreateSubmissionState,
    ) {
        val operationKey = operationKeyAllocator.next()
        _uiState.update { state ->
            if (state.submission == expectedSubmission && state.colorSheet is StampColorSheetState.Closed) {
                state.copy(submission = GroupCreateSubmissionState.Submitting(operationKey))
            } else {
                state
            }
        }
        if (_uiState.value.submission != GroupCreateSubmissionState.Submitting(operationKey)) return

        viewModelScope.launch {
            val pending =
                PersistedGroupCreateOperation(
                    ownerInstanceId = operationKey.ownerInstanceId,
                    sequence = operationKey.sequence,
                    draft = draft.toPersistedDraft(),
                )
            pendingOperation = pending
            try {
                sessionWriteMutex.withLock {
                    sessionStore.write(
                        GroupCreateSessionSnapshot(
                            draft = _uiState.value.draft.toPersistedDraft(),
                            pendingOperation = pending,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                pendingOperation = null
                errorReporter.reportUnexpected(exception)
                _uiState.update { state ->
                    val submitting = state.submission as? GroupCreateSubmissionState.Submitting
                    if (submitting?.operationKey == operationKey) {
                        state.copy(
                            submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.Unavailable),
                            restoration = GroupCreateRestorationState.Unavailable,
                        )
                    } else {
                        state
                    }
                }
                return@launch
            }

            val result =
                try {
                    withTimeoutOrNull(requestPolicy.timeoutMillis) {
                        createGroupAction.create(draft)
                    } ?: CreateGroupResult.OutcomeUnknown
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    errorReporter.reportUnexpected(exception)
                    CreateGroupResult.Unavailable
                }

            _uiState.update { state ->
                val submitting = state.submission as? GroupCreateSubmissionState.Submitting
                if (submitting?.operationKey != operationKey) return@update state

                when (result) {
                    is CreateGroupResult.Created -> {
                        state.copy(
                            submission = GroupCreateSubmissionState.Succeeded(operationKey, result.groupId),
                            isRecoveryDialogVisible = false,
                        )
                    }

                    CreateGroupResult.DuplicateName -> {
                        pendingOperation = null
                        state.copy(
                            submission = GroupCreateSubmissionState.Editing,
                            fieldErrors = state.fieldErrors.copy(name = GroupCreateFieldError.Duplicate),
                            isRecoveryDialogVisible = false,
                        )
                    }

                    CreateGroupResult.InvalidInput -> {
                        pendingOperation = null
                        state.copy(submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.InvalidInput))
                    }

                    CreateGroupResult.RateLimited -> {
                        pendingOperation = null
                        state.copy(submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.RateLimited))
                    }

                    CreateGroupResult.Unavailable -> {
                        pendingOperation = null
                        state.copy(submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.Unavailable))
                    }

                    CreateGroupResult.OutcomeUnknown -> {
                        state.copy(
                            submission =
                                GroupCreateSubmissionState.Failed(
                                    reason = GroupCreateFailure.OutcomeUnknown,
                                    operationKey = operationKey,
                                ),
                            recovery = GroupCreateRecoveryState.Idle,
                            isRecoveryDialogVisible = true,
                        )
                    }
                }
            }
            if (result !is CreateGroupResult.Created && result != CreateGroupResult.OutcomeUnknown) {
                persistCurrentSession()
            }
        }
    }

    fun onDismissFailure() {
        _uiState.update { state ->
            val failed = state.submission as? GroupCreateSubmissionState.Failed
            if (failed?.reason == GroupCreateFailure.OutcomeUnknown && pendingOperation != null) {
                state.copy(isRecoveryDialogVisible = false)
            } else if (failed != null) {
                state.copy(
                    submission = GroupCreateSubmissionState.Editing,
                    recovery = GroupCreateRecoveryState.Idle,
                )
            } else {
                state
            }
        }
        if (pendingOperation == null) persistCurrentSession()
    }

    /** Back 입력을 상태에 따라 처리하고, true일 때만 Route가 화면 종료를 요청합니다. */
    fun onBackRequested(): Boolean {
        val state = _uiState.value
        return when {
            state.isDiscardInProgress -> {
                false
            }

            state.isDiscardConfirmationVisible -> {
                onCancelDraftDiscard()
                false
            }

            state.restoration == GroupCreateRestorationState.Restoring -> {
                false
            }

            state.colorSheet is StampColorSheetState.Editing -> {
                closeColorSheet()
                false
            }

            state.submission == GroupCreateSubmissionState.Confirming -> {
                onCancelConfirmation()
                false
            }

            state.submission is GroupCreateSubmissionState.Failed -> {
                if (state.submission.reason == GroupCreateFailure.OutcomeUnknown && pendingOperation != null) {
                    if (state.isRecoveryDialogVisible) {
                        onDismissFailure()
                        false
                    } else {
                        telemetry.emit("group_flow_closed", labels("operation_kind" to "create"))
                        true
                    }
                } else {
                    onDismissFailure()
                    false
                }
            }

            state.submission == GroupCreateSubmissionState.Editing -> {
                if (state.hasEditedDraft) {
                    _uiState.update { current -> current.copy(isDiscardConfirmationVisible = true) }
                    false
                } else {
                    telemetry.emit("group_flow_closed", labels("operation_kind" to "create"))
                    true
                }
            }

            else -> {
                false
            }
        }
    }

    fun onCancelDraftDiscard() {
        _uiState.update { state ->
            if (state.isDiscardInProgress) {
                state
            } else {
                state.copy(isDiscardConfirmationVisible = false, discardFailed = false)
            }
        }
    }

    /** Clears the editable draft durably before the route leaves; an unresolved operation is never discarded here. */
    suspend fun confirmDraftDiscardAndExit(): Boolean {
        val previous = _uiState.value
        if (!previous.isDiscardConfirmationVisible || previous.isDiscardInProgress || pendingOperation != null) {
            return false
        }
        val previousDraftUpdatedAt = draftUpdatedAtEpochMillis
        draftUpdatedAtEpochMillis = null
        _uiState.update { state ->
            state.copy(
                draft = DEFAULT_GROUP_CREATE_DRAFT,
                fieldErrors = GroupCreateFieldErrors(),
                submission = GroupCreateSubmissionState.Editing,
                recovery = GroupCreateRecoveryState.Idle,
                isDiscardInProgress = true,
                isDraftExpiredNoticeVisible = false,
                discardFailed = false,
            )
        }
        return try {
            sessionWriteMutex.withLock { sessionStore.write(null) }
            _uiState.update { state ->
                state.copy(isDiscardConfirmationVisible = false, isDiscardInProgress = false)
            }
            telemetry.emit("group_flow_closed", labels("operation_kind" to "create", "action" to "discard"))
            true
        } catch (cancelled: CancellationException) {
            draftUpdatedAtEpochMillis = previousDraftUpdatedAt
            _uiState.value = previous
            throw cancelled
        } catch (exception: Exception) {
            errorReporter.reportUnexpected(exception)
            draftUpdatedAtEpochMillis = previousDraftUpdatedAt
            _uiState.update { state ->
                state.copy(
                    draft = previous.draft,
                    fieldErrors = previous.fieldErrors,
                    submission = previous.submission,
                    recovery = previous.recovery,
                    isDiscardInProgress = false,
                    isDiscardConfirmationVisible = true,
                    discardFailed = true,
                )
            }
            false
        }
    }

    fun openColorSheet() {
        _uiState.update { state ->
            if (!state.canOpenColorSheet) return@update state
            val initial = ColorConversion.toSelection(state.draft.stamp.fillArgb)
            val hueToPreserve = lastAppliedHue
            state.copy(
                colorSheet =
                    StampColorSheetState.Editing(
                        selection =
                            if (initial.saturation == 0f && hueToPreserve != null) {
                                initial.copy(hueDegrees = hueToPreserve)
                            } else {
                                initial
                            },
                    ),
            )
        }
    }

    fun onColorSelectionChanged(selection: StampColorSelection) {
        _uiState.update { state ->
            if (state.submission != GroupCreateSubmissionState.Editing ||
                state.colorSheet !is StampColorSheetState.Editing
            ) {
                state
            } else {
                state.copy(colorSheet = StampColorSheetState.Editing(selection))
            }
        }
    }

    fun applyColorSelection() {
        val current = _uiState.value
        val selection = (current.colorSheet as? StampColorSheetState.Editing)?.selection ?: return
        if (current.restoration != GroupCreateRestorationState.Ready || pendingOperation != null ||
            current.submission != GroupCreateSubmissionState.Editing
        ) {
            return
        }
        lastAppliedHue = selection.hueDegrees
        val fillArgb = ColorConversion.toArgb(selection)
        var draftChanged = false
        _uiState.update { state ->
            val activeSelection = (state.colorSheet as? StampColorSheetState.Editing)?.selection
            if (state.submission != GroupCreateSubmissionState.Editing ||
                activeSelection != selection
            ) {
                return@update state
            }
            val updatedDraft = state.draft.copy(stamp = state.draft.stamp.copy(fillArgb = fillArgb))
            draftChanged = updatedDraft != state.draft
            state.copy(
                draft = updatedDraft,
                colorSheet = StampColorSheetState.Closed,
            )
        }
        if (draftChanged) markDraftEditedAndPersist()
    }

    fun closeColorSheet() {
        _uiState.update { state ->
            if (state.colorSheet is StampColorSheetState.Editing) {
                state.copy(colorSheet = StampColorSheetState.Closed)
            } else {
                state
            }
        }
    }

    /** Route가 성공 결과를 처리한 뒤 같은 operation key일 때만 종결 상태로 바꿉니다. */
    fun acknowledgeCreated(operationKey: GroupOperationKey) {
        _uiState.update { state ->
            val success = state.submission as? GroupCreateSubmissionState.Succeeded
            if (success?.operationKey == operationKey) {
                state.copy(submission = GroupCreateSubmissionState.Acknowledged)
            } else {
                state
            }
        }
        if (_uiState.value.submission == GroupCreateSubmissionState.Acknowledged) {
            pendingOperation = null
            clearPersistedSession()
        }
    }

    private fun updateDraft(
        transform: (GroupCreateDraft) -> GroupCreateDraft,
        clearError: (GroupCreateFieldErrors) -> GroupCreateFieldErrors,
    ) {
        var draftChanged = false
        _uiState.update { state ->
            if (state.restoration != GroupCreateRestorationState.Ready || pendingOperation != null ||
                (
                    state.submission != GroupCreateSubmissionState.Editing &&
                        state.submission !is GroupCreateSubmissionState.Failed
                ) ||
                state.colorSheet !is StampColorSheetState.Closed
            ) {
                return@update state
            }
            val updatedDraft = transform(state.draft)
            draftChanged = updatedDraft != state.draft
            state.copy(
                draft = updatedDraft,
                fieldErrors = clearError(state.fieldErrors),
                submission = GroupCreateSubmissionState.Editing,
                recovery = GroupCreateRecoveryState.Idle,
            )
        }
        if (draftChanged) markDraftEditedAndPersist()
    }

    private fun updateDraft(transform: (GroupCreateDraft) -> GroupCreateDraft) {
        var draftChanged = false
        _uiState.update { state ->
            if (state.restoration != GroupCreateRestorationState.Ready || pendingOperation != null ||
                state.submission != GroupCreateSubmissionState.Editing ||
                state.colorSheet !is StampColorSheetState.Closed
            ) {
                state
            } else {
                val updatedDraft = transform(state.draft)
                draftChanged = updatedDraft != state.draft
                state.copy(draft = updatedDraft)
            }
        }
        if (draftChanged) markDraftEditedAndPersist()
    }

    private fun markDraftEditedAndPersist() {
        draftUpdatedAtEpochMillis = nowMillis()
        persistCurrentSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            try {
                val snapshot = sessionWriteMutex.withLock { sessionStore.read() }
                val restoredOperation = snapshot?.pendingOperation
                val restoredDraft = restoredOperation?.draft?.toDraft() ?: snapshot?.draft?.toDraft()
                val now = nowMillis()
                val restoredTimestamp =
                    snapshot?.draftUpdatedAtEpochMillis?.coerceAtMost(now)
                        ?: now.takeIf { restoredDraft != null && restoredDraft != DEFAULT_GROUP_CREATE_DRAFT }
                val hasDraft = restoredDraft != null && restoredDraft != DEFAULT_GROUP_CREATE_DRAFT

                if (restoredOperation == null && hasDraft && restoredTimestamp != null &&
                    now >= restoredTimestamp && now - restoredTimestamp >= GroupCreateSessionSnapshot.DRAFT_TTL_MILLIS
                ) {
                    sessionWriteMutex.withLock { sessionStore.write(null) }
                    draftUpdatedAtEpochMillis = null
                    _uiState.update { state ->
                        state.copy(
                            draft = DEFAULT_GROUP_CREATE_DRAFT,
                            restoration = GroupCreateRestorationState.Ready,
                            isDraftExpiredNoticeVisible = true,
                        )
                    }
                    return@launch
                }

                val migratedSnapshot =
                    snapshot?.copy(
                        schemaVersion = GroupCreateSessionSnapshot.CURRENT_SCHEMA_VERSION,
                        draft = restoredDraft?.toPersistedDraft() ?: DEFAULT_GROUP_CREATE_DRAFT.toPersistedDraft(),
                        draftUpdatedAtEpochMillis = restoredTimestamp,
                    )
                if (snapshot != null && restoredOperation == null && !hasDraft) {
                    sessionWriteMutex.withLock { sessionStore.write(null) }
                } else if (migratedSnapshot != null && migratedSnapshot != snapshot) {
                    sessionWriteMutex.withLock { sessionStore.write(migratedSnapshot) }
                }

                pendingOperation = restoredOperation
                draftUpdatedAtEpochMillis = restoredTimestamp
                val restoredKey = restoredOperation?.toOperationKey()
                _uiState.update { state ->
                    state.copy(
                        draft = restoredDraft ?: state.draft,
                        submission =
                            restoredKey?.let {
                                GroupCreateSubmissionState.Failed(GroupCreateFailure.OutcomeUnknown, it)
                            } ?: GroupCreateSubmissionState.Editing,
                        restoration = GroupCreateRestorationState.Ready,
                        recovery = GroupCreateRecoveryState.Idle,
                        isRecoveryDialogVisible = restoredOperation != null,
                    )
                }
                if (restoredOperation != null) onCheckGroupsAfterUnknownOutcome()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                errorReporter.reportUnexpected(exception)
                val recovered =
                    try {
                        sessionWriteMutex.withLock { sessionStore.clearCorruptedDraftIfSafe() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (cleanupException: Exception) {
                        errorReporter.reportUnexpected(cleanupException)
                        false
                    }
                if (recovered) {
                    pendingOperation = null
                    draftUpdatedAtEpochMillis = null
                    _uiState.update { state ->
                        state.copy(
                            draft = DEFAULT_GROUP_CREATE_DRAFT,
                            fieldErrors = GroupCreateFieldErrors(),
                            submission = GroupCreateSubmissionState.Editing,
                            recovery = GroupCreateRecoveryState.Idle,
                            restoration = GroupCreateRestorationState.Ready,
                            isRecoveryDialogVisible = false,
                            isDraftResetNoticeVisible = true,
                        )
                    }
                } else {
                    _uiState.update { state -> state.copy(restoration = GroupCreateRestorationState.Unavailable) }
                }
            }
        }
    }

    private fun persistCurrentSession() {
        viewModelScope.launch {
            try {
                sessionWriteMutex.withLock {
                    val state = _uiState.value
                    val shouldPersistDraft = state.hasEditedDraft
                    if (!shouldPersistDraft && pendingOperation == null) {
                        sessionStore.write(null)
                    } else {
                        sessionStore.write(
                            GroupCreateSessionSnapshot(
                                draft = state.draft.toPersistedDraft(),
                                draftUpdatedAtEpochMillis =
                                    draftUpdatedAtEpochMillis
                                        ?: nowMillis().takeIf { shouldPersistDraft || pendingOperation != null },
                                pendingOperation = pendingOperation,
                            ),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                errorReporter.reportUnexpected(exception)
                _uiState.update { state -> state.copy(restoration = GroupCreateRestorationState.Unavailable) }
            }
        }
    }

    private fun clearPersistedSession() {
        viewModelScope.launch {
            try {
                sessionWriteMutex.withLock { sessionStore.write(null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                errorReporter.reportUnexpected(exception)
                _uiState.update { state -> state.copy(restoration = GroupCreateRestorationState.Unavailable) }
            }
        }
    }
}

private fun GroupCreateDraft.toPersistedDraft() =
    PersistedGroupCreateDraft(
        name = name,
        description = description,
        stampLabel = stamp.label,
        stampShape = stamp.shape.name,
        stampFillArgb = stamp.fillArgb,
        stampTextArgb = stamp.textArgb,
    )

private fun PersistedGroupCreateDraft.toDraft() =
    GroupCreateDraft(
        name = name,
        description = description,
        stamp =
            StampAppearanceUiModel(
                label = stampLabel,
                shape = StampShapeId.valueOf(stampShape),
                fillArgb = stampFillArgb,
                textArgb = stampTextArgb,
            ),
    )

private fun PersistedGroupCreateOperation.toOperationKey() =
    GroupOperationKey(
        ownerInstanceId = ownerInstanceId,
        sequence = sequence,
    )
