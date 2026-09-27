package com.pheeeew.feature.screens.group.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.preview.CreateFixtures

@Preview(name = "그룹 생성 · 입력")
@Composable
private fun GroupCreateFormPreview() {
    var uiState by remember { mutableStateOf(GroupCreateUiState()) }
    GroupCreatePreviewFrame(
        uiState = uiState,
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
        onStampLabelChanged = {},
        onStampShapeChanged = {},
        onStampTextColorChanged = {},
        onCreateClick = {},
        onCancelConfirmation = {},
        onConfirmCreate = {},
        onDismissFailure = {},
        onRetryFailure = {},
        onOpenColorSheet = onOpenColorSheet,
        onColorSelectionChanged = onColorSelectionChanged,
        onCloseColorSheet = onCloseColorSheet,
        onApplyColor = onApplyColor,
    )
}
