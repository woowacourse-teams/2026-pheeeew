package com.pheeeew.feature.screens.group.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.feature.screens.group.create.ColorConversion
import com.pheeeew.feature.screens.group.create.GroupCreateScreen
import com.pheeeew.feature.screens.group.create.GroupCreateUiState
import com.pheeeew.feature.screens.group.create.GroupFormRules
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.model.GroupJoinType

@Preview(name = "그룹 생성 · 입력")
@Composable
private fun GroupCreateFormPreview() {
    var uiState by remember { mutableStateOf(GroupCreateUiState()) }
    GroupCreatePreviewFrame(
        uiState = uiState,
        onJoinTypeChanged = { joinType -> uiState = uiState.copy(draft = uiState.draft.copy(joinType = joinType)) },
        onOpenColorSheet = {
            val selection = ColorConversion.toSelection(uiState.draft.stamp.fillArgb)
            uiState = uiState.copy(colorSheet = StampColorSheetState.Editing(selection))
        },
        onColorSelectionChanged = { selection ->
            uiState = uiState.copy(colorSheet = StampColorSheetState.Editing(selection))
        },
        onCloseColorSheet = {
            uiState = uiState.copy(colorSheet = StampColorSheetState.Closed)
        },
        onApplyColor = {
            val selection = (uiState.colorSheet as? StampColorSheetState.Editing)?.selection
            if (selection != null) {
                uiState =
                    uiState.copy(
                        draft =
                            uiState.draft.copy(
                                stamp = uiState.draft.stamp.copy(fillArgb = ColorConversion.toArgb(selection)),
                            ),
                        colorSheet = StampColorSheetState.Closed,
                    )
            }
        },
    )
}

@Preview(name = "그룹 생성 · 입력 완료")
@Composable
private fun GroupCreateFilledPreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.filled)
}

@Preview(name = "그룹 생성 · 공개 가입 방식")
@Composable
private fun GroupCreatePublicJoinTypePreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.publicJoinType)
}

@Preview(name = "그룹 생성 · 그룹명 중복")
@Composable
private fun GroupCreateDuplicateNamePreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.duplicateName)
}

@Preview(name = "그룹 생성 · 글자 수 오류")
@Composable
private fun GroupCreateLengthErrorsPreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.lengthErrors)
}

@Preview(name = "그룹 생성 · 확인")
@Composable
private fun GroupCreateConfirmationPreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.confirming)
}

@Preview(name = "그룹 생성 · 제출 중")
@Composable
private fun GroupCreateSubmittingPreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.submitting)
}

@Preview(name = "그룹 생성 · 색상 바텀시트 표시 상태")
@Composable
private fun GroupCreateColorSheetPreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.choosingColor)
}

@Preview(name = "그룹 생성 · 실패")
@Composable
private fun GroupCreateFailurePreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.failed)
}

@Preview(name = "그룹 생성 · 결과 불명")
@Composable
private fun GroupCreateUnknownOutcomePreview() {
    GroupCreatePreviewFrame(uiState = CreateFixtures.unknownOutcome)
}

@Composable
private fun GroupCreatePreviewFrame(
    uiState: GroupCreateUiState,
    onJoinTypeChanged: (GroupJoinType) -> Unit = {},
    onOpenColorSheet: () -> Unit = {},
    onColorSelectionChanged: (StampColorSelection) -> Unit = {},
    onCloseColorSheet: () -> Unit = {},
    onApplyColor: () -> Unit = {},
) {
    GroupCreateScreen(
        uiState = uiState,
        formRules = GroupFormRules(),
        onBack = {},
        onNameChanged = {},
        onDescriptionChanged = {},
        onJoinTypeChanged = onJoinTypeChanged,
        onStampLabelChanged = {},
        onStampShapeChanged = {},
        onStampTextColorChanged = {},
        onCreateClick = {},
        onCancelConfirmation = {},
        onCancelDraftDiscard = {},
        onConfirmDraftDiscard = {},
        onConfirmCreate = {},
        onDismissFailure = {},
        onShowRecoveryDialog = {},
        onRetryFailure = {},
        onOpenColorSheet = onOpenColorSheet,
        onColorSelectionChanged = onColorSelectionChanged,
        onCloseColorSheet = onCloseColorSheet,
        onApplyColor = onApplyColor,
    )
}

@Preview(name = "그룹 생성 · 새 초안 기본 공개", widthDp = 360, heightDp = 800)
@Composable
private fun GroupCreateDefaultPublicPreview() {
    GroupCreatePreviewFrame(uiState = GroupCreateUiState())
}
