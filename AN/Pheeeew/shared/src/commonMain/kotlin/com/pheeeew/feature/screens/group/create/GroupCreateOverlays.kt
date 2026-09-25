package com.pheeeew.feature.screens.group.create

import androidx.compose.runtime.Composable
import com.pheeeew.feature.screens.group.create.component.CreateConfirmationDialog
import com.pheeeew.feature.screens.group.create.component.CreateFailureDialog
import com.pheeeew.feature.screens.group.create.component.StampColorSheet
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState

@Composable
internal fun GroupCreateOverlays(
    uiState: GroupCreateUiState,
    onCancelConfirmation: () -> Unit,
    onConfirmCreate: () -> Unit,
    onDismissFailure: () -> Unit,
    onRetryFailure: () -> Unit,
    onColorSelectionChanged: (StampColorSelection) -> Unit,
    onCloseColorSheet: () -> Unit,
    onApplyColor: () -> Unit,
) {
    val colorEditing = uiState.colorSheet as? StampColorSheetState.Editing
    if (colorEditing != null) {
        StampColorSheet(
            appearance = uiState.draft.stamp,
            selection = colorEditing.selection,
            onSelectionChanged = onColorSelectionChanged,
            onDismiss = onCloseColorSheet,
            onApply = onApplyColor,
        )
    }

    when (val submission = uiState.submission) {
        GroupCreateSubmissionState.Confirming ->
            CreateConfirmationDialog(
                isSubmitting = false,
                groupName = uiState.draft.name,
                stampLabel = uiState.draft.stamp.label,
                onDismiss = onCancelConfirmation,
                onConfirm = onConfirmCreate,
            )

        is GroupCreateSubmissionState.Submitting ->
            CreateConfirmationDialog(
                isSubmitting = true,
                groupName = uiState.draft.name,
                stampLabel = uiState.draft.stamp.label,
                onDismiss = {},
                onConfirm = {},
            )

        is GroupCreateSubmissionState.Failed ->
            CreateFailureDialog(
                unknownOutcome = submission.reason == GroupCreateFailure.OutcomeUnknown,
                onDismiss = onDismissFailure,
                onRetry = onRetryFailure,
            )

        GroupCreateSubmissionState.Editing,
        is GroupCreateSubmissionState.Succeeded,
        GroupCreateSubmissionState.Acknowledged,
        -> Unit
    }
}
