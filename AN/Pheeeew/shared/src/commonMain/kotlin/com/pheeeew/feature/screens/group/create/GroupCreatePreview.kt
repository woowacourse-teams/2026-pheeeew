package com.pheeeew.feature.screens.group.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.feature.screens.group.preview.CreateFixtures

@Preview(name = "그룹 생성 · 입력")
@Composable
private fun GroupCreateFormPreview() {
    GroupCreatePreviewFrame(uiState = GroupCreateUiState())
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

@Preview(name = "그룹 생성 · 색상 선택")
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
private fun GroupCreatePreviewFrame(uiState: GroupCreateUiState) {
    GroupCreateScreen(
        uiState = uiState,
        formRules = GroupFormRules(),
        onBack = {},
        onNameChanged = {},
        onDescriptionChanged = {},
        onStampLabelChanged = {},
        onStampShapeChanged = {},
        onCreateClick = {},
        onCancelConfirmation = {},
        onConfirmCreate = {},
        onDismissFailure = {},
        onRetryFailure = {},
        onOpenColorSheet = {},
        onColorSelectionChanged = {},
        onCloseColorSheet = {},
        onApplyColor = {},
    )
}
