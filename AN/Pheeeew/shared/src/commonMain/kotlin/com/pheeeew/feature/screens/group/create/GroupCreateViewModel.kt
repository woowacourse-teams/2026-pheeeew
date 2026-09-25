package com.pheeeew.feature.screens.group.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 생성 초안, 검증, 확인, 제출 및 결과 전달 수명을 소유합니다. */
class GroupCreateViewModel(
    private val createGroupAction: CreateGroupAction,
    private val errorReporter: GroupCreateErrorReporter,
    private val operationKeyAllocator: GroupOperationKeyAllocator,
    val formRules: GroupFormRules = GroupFormRules(),
    private val requestPolicy: CreateRequestPolicy = CreateRequestPolicy(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupCreateUiState())
    val uiState = _uiState.asStateFlow()
    private var lastAppliedHue: Float? = null

    fun onNameChanged(value: String) {
        updateDraft(
            transform = { draft -> draft.copy(name = formRules.retainGroupNameForValidation(value)) },
            clearError = { errors -> errors.copy(name = null) },
        )
    }

    fun onDescriptionChanged(value: String) {
        updateDraft(
            transform = { draft -> draft.copy(description = formRules.retainDescriptionForValidation(value)) },
            clearError = { errors -> errors.copy(description = null) },
        )
    }

    fun onStampLabelChanged(value: String) {
        updateDraft(
            transform = { draft ->
                draft.copy(stamp = draft.stamp.copy(label = formRules.retainStampLabelForValidation(value)))
            },
            clearError = { errors -> errors.copy(stampLabel = null) },
        )
    }

    fun onStampShapeChanged(shape: StampShapeId) {
        updateDraft { draft -> draft.copy(stamp = draft.stamp.copy(shape = shape)) }
    }

    fun onCreateClick() {
        val current = _uiState.value
        if (current.submission != GroupCreateSubmissionState.Editing ||
            current.colorSheet !is StampColorSheetState.Closed
        ) {
            return
        }

        val errors = formRules.validate(current.draft)
        if (errors.hasErrors) {
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
        if (current.submission != GroupCreateSubmissionState.Confirming ||
            current.colorSheet !is StampColorSheetState.Closed
        ) {
            return
        }

        submitDraft(
            draft = current.draft.normalizedForSubmission(),
            expectedSubmission = GroupCreateSubmissionState.Confirming,
        )
    }

    /** 실패 팝업의 명시적인 재시도 동작입니다. 자동 재시도는 하지 않습니다. */
    fun onRetryFailure() {
        val current = _uiState.value
        val failed = current.submission as? GroupCreateSubmissionState.Failed ?: return
        if (current.colorSheet !is StampColorSheetState.Closed) return

        submitDraft(
            draft = current.draft.normalizedForSubmission(),
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
                        )
                    }

                    CreateGroupResult.DuplicateName -> {
                        state.copy(
                            submission = GroupCreateSubmissionState.Editing,
                            fieldErrors = state.fieldErrors.copy(name = GroupCreateFieldError.Duplicate),
                        )
                    }

                    CreateGroupResult.Unavailable -> {
                        state.copy(submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.Unavailable))
                    }

                    CreateGroupResult.OutcomeUnknown -> {
                        state.copy(submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.OutcomeUnknown))
                    }
                }
            }
        }
    }

    fun onDismissFailure() {
        _uiState.update { state ->
            if (state.submission is GroupCreateSubmissionState.Failed) {
                state.copy(submission = GroupCreateSubmissionState.Editing)
            } else {
                state
            }
        }
    }

    /** Back 입력을 상태에 따라 처리하고, true일 때만 Route가 화면 종료를 요청합니다. */
    fun onBackRequested(): Boolean {
        val state = _uiState.value
        return when {
            state.colorSheet is StampColorSheetState.Editing -> {
                closeColorSheet()
                false
            }

            state.submission == GroupCreateSubmissionState.Confirming -> {
                onCancelConfirmation()
                false
            }

            state.submission is GroupCreateSubmissionState.Failed -> {
                onDismissFailure()
                false
            }

            state.submission == GroupCreateSubmissionState.Editing -> {
                true
            }

            else -> {
                false
            }
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
        if (current.submission != GroupCreateSubmissionState.Editing) return
        lastAppliedHue = selection.hueDegrees
        val fillArgb = ColorConversion.toArgb(selection)
        _uiState.update { state ->
            val activeSelection = (state.colorSheet as? StampColorSheetState.Editing)?.selection
            if (state.submission != GroupCreateSubmissionState.Editing ||
                activeSelection != selection
            ) {
                return@update state
            }
            state.copy(
                draft = state.draft.copy(stamp = state.draft.stamp.copy(fillArgb = fillArgb)),
                colorSheet = StampColorSheetState.Closed,
            )
        }
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
    }

    private fun updateDraft(
        transform: (GroupCreateDraft) -> GroupCreateDraft,
        clearError: (GroupCreateFieldErrors) -> GroupCreateFieldErrors,
    ) {
        _uiState.update { state ->
            if (
                (
                    state.submission != GroupCreateSubmissionState.Editing &&
                        state.submission !is GroupCreateSubmissionState.Failed
                ) ||
                state.colorSheet !is StampColorSheetState.Closed
            ) {
                return@update state
            }
            state.copy(
                draft = transform(state.draft),
                fieldErrors = clearError(state.fieldErrors),
                submission = GroupCreateSubmissionState.Editing,
            )
        }
    }

    private fun updateDraft(transform: (GroupCreateDraft) -> GroupCreateDraft) {
        _uiState.update { state ->
            if (state.submission != GroupCreateSubmissionState.Editing ||
                state.colorSheet !is StampColorSheetState.Closed
            ) {
                state
            } else {
                state.copy(draft = transform(state.draft))
            }
        }
    }
}
