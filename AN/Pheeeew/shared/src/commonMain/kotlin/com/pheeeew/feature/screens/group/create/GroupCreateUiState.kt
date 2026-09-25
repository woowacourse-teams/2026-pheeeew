package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey

/** 그룹 생성 화면의 단일 상태 스냅샷입니다. */
data class GroupCreateUiState(
    val draft: GroupCreateDraft =
        GroupCreateDraft(
            name = "",
            description = "",
            stamp =
                StampAppearanceUiModel(
                    label = "",
                    shape = StampShapeId.CIRCLE,
                    fillArgb = DEFAULT_STAMP_FILL,
                    textArgb = DEFAULT_STAMP_TEXT,
                ),
        ),
    val fieldErrors: GroupCreateFieldErrors = GroupCreateFieldErrors(),
    val submission: GroupCreateSubmissionState = GroupCreateSubmissionState.Editing,
    val colorSheet: StampColorSheetState = StampColorSheetState.Closed,
) {
    val canOpenColorSheet: Boolean
        get() = submission == GroupCreateSubmissionState.Editing && colorSheet is StampColorSheetState.Closed

    private companion object {
        const val DEFAULT_STAMP_FILL = 0xFFA7DCCFL
        const val DEFAULT_STAMP_TEXT = 0xFF202323L
    }
}

sealed interface GroupCreateSubmissionState {
    data object Editing : GroupCreateSubmissionState

    data object Confirming : GroupCreateSubmissionState

    data class Submitting(
        val operationKey: GroupOperationKey,
    ) : GroupCreateSubmissionState

    data class Failed(
        val reason: GroupCreateFailure,
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
    OutcomeUnknown,
}
