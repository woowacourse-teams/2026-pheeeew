package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.create.model.StampTextColorOption
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey

internal val DEFAULT_GROUP_CREATE_DRAFT =
    GroupCreateDraft(
        name = "",
        description = "",
        stamp =
            StampAppearanceUiModel(
                label = "",
                shape = StampShapeId.CIRCLE,
                fillArgb = 0xFFA7DCCFL,
                textArgb = StampTextColorOption.BLACK.argb,
            ),
    )

/** 그룹 생성 화면의 단일 상태 스냅샷입니다. */
data class GroupCreateUiState(
    val draft: GroupCreateDraft = DEFAULT_GROUP_CREATE_DRAFT,
    val fieldErrors: GroupCreateFieldErrors = GroupCreateFieldErrors(),
    val submission: GroupCreateSubmissionState = GroupCreateSubmissionState.Editing,
    val colorSheet: StampColorSheetState = StampColorSheetState.Closed,
    val recovery: GroupCreateRecoveryState = GroupCreateRecoveryState.Idle,
    val restoration: GroupCreateRestorationState = GroupCreateRestorationState.Ready,
    val isRecoveryDialogVisible: Boolean = false,
    val isDiscardConfirmationVisible: Boolean = false,
    val isDiscardInProgress: Boolean = false,
    val isDraftExpiredNoticeVisible: Boolean = false,
    val isDraftResetNoticeVisible: Boolean = false,
    val discardFailed: Boolean = false,
) {
    val hasEditedDraft: Boolean
        get() = draft != DEFAULT_GROUP_CREATE_DRAFT

    val canOpenColorSheet: Boolean
        get() =
            restoration == GroupCreateRestorationState.Ready &&
                submission == GroupCreateSubmissionState.Editing &&
                colorSheet is StampColorSheetState.Closed
}

enum class GroupCreateRestorationState {
    Restoring,
    Ready,
    Unavailable,
}

sealed interface GroupCreateSubmissionState {
    data object Editing : GroupCreateSubmissionState

    data object Confirming : GroupCreateSubmissionState

    data class Submitting(
        val operationKey: GroupOperationKey,
    ) : GroupCreateSubmissionState

    data class Failed(
        val reason: GroupCreateFailure,
        val operationKey: GroupOperationKey? = null,
    ) : GroupCreateSubmissionState

    data class Succeeded(
        val operationKey: GroupOperationKey,
        val groupId: GroupId,
    ) : GroupCreateSubmissionState

    /** 성공 결과가 Route에 전달된 뒤 생성 화면을 제거할 때까지 유지하는 종결 상태입니다. */
    data object Acknowledged : GroupCreateSubmissionState
}

enum class GroupCreateFailure {
    Unavailable,
    InvalidInput,
    RateLimited,
    OutcomeUnknown,
}
