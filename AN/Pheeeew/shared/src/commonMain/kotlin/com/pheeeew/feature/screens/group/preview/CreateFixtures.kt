package com.pheeeew.feature.screens.group.preview

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.GroupCreateDraft
import com.pheeeew.feature.screens.group.create.GroupCreateFailure
import com.pheeeew.feature.screens.group.create.GroupCreateFieldError
import com.pheeeew.feature.screens.group.create.GroupCreateFieldErrors
import com.pheeeew.feature.screens.group.create.GroupCreateSubmissionState
import com.pheeeew.feature.screens.group.create.GroupCreateUiState
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.model.GroupOperationKey

internal object CreateFixtures {
    val validDraft =
        GroupCreateDraft(
            name = "우테코 8기",
            description = "함께 감정을 나누는 그룹",
            stamp =
                StampAppearanceUiModel(
                    label = "히유",
                    shape = StampShapeId.CIRCLE,
                    fillArgb = 0xFFA7DCCFL,
                    textArgb = 0xFF202323L,
                ),
        )

    val filled = GroupCreateUiState(draft = validDraft)

    val confirming =
        filled.copy(submission = GroupCreateSubmissionState.Confirming)

    val duplicateName =
        filled.copy(
            fieldErrors = GroupCreateFieldErrors(name = GroupCreateFieldError.Duplicate),
        )

    val lengthErrors =
        GroupCreateUiState(
            draft =
                validDraft.copy(
                    name = "그룹 이름 입력 초과",
                    description = "설명 글자 수 제한을 넘긴 입력입니다.",
                    stamp = validDraft.stamp.copy(label = "문구 초과"),
                ),
            fieldErrors =
                GroupCreateFieldErrors(
                    name = GroupCreateFieldError.TooLong,
                    description = GroupCreateFieldError.TooLong,
                    stampLabel = GroupCreateFieldError.TooLong,
                ),
        )

    val failed =
        filled.copy(submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.Unavailable))

    val unknownOutcome =
        filled.copy(submission = GroupCreateSubmissionState.Failed(GroupCreateFailure.OutcomeUnknown))

    val choosingColor =
        filled.copy(
            colorSheet =
                StampColorSheetState.Editing(
                    selection = StampColorSelection(hueDegrees = 168f, saturation = 0.34f, value = 0.86f),
                ),
        )

    val submitting =
        filled.copy(
            submission = GroupCreateSubmissionState.Submitting(GroupOperationKey("preview", 1L)),
        )
}
