package com.pheeeew.feature.screens.group.create

import androidx.compose.runtime.Composable
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.feature.screens.group.create.component.CreateConfirmationDialog
import com.pheeeew.feature.screens.group.create.component.CreateFailureDialog
import com.pheeeew.feature.screens.group.create.component.CreateOutcomeUnknownDialog
import com.pheeeew.feature.screens.group.create.component.StampColorSheet
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.model.GroupId
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_discard_body
import pheeeew.shared.generated.resources.group_create_discard_confirm
import pheeeew.shared.generated.resources.group_create_discard_failed
import pheeeew.shared.generated.resources.group_create_discard_retry
import pheeeew.shared.generated.resources.group_create_discard_title
import pheeeew.shared.generated.resources.group_create_keep_editing

@Composable
internal fun GroupCreateOverlays(
    uiState: GroupCreateUiState,
    onCancelConfirmation: () -> Unit,
    onCancelDraftDiscard: () -> Unit,
    onConfirmDraftDiscard: () -> Unit,
    onConfirmCreate: () -> Unit,
    onDismissFailure: () -> Unit,
    onRetryFailure: () -> Unit,
    onCheckGroupsAfterUnknownOutcome: () -> Unit,
    onSelectRecoveryCandidate: (GroupId) -> Unit,
    onRetryUnknownCreation: () -> Unit,
    onColorSelectionChanged: (StampColorSelection) -> Unit,
    onCloseColorSheet: () -> Unit,
    onApplyColor: () -> Unit,
) {
    if (uiState.isDiscardConfirmationVisible) {
        ConfirmDialog(
            title = stringResource(Res.string.group_create_discard_title),
            content =
                stringResource(
                    if (uiState.discardFailed) {
                        Res.string.group_create_discard_failed
                    } else {
                        Res.string.group_create_discard_body
                    },
                ),
            confirmText =
                stringResource(
                    if (uiState.discardFailed) {
                        Res.string.group_create_discard_retry
                    } else {
                        Res.string.group_create_discard_confirm
                    },
                ),
            cancelText = stringResource(Res.string.group_create_keep_editing),
            onConfirm = onConfirmDraftDiscard,
            onCancel = onCancelDraftDiscard,
        )
    }

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
        GroupCreateSubmissionState.Confirming -> {
            CreateConfirmationDialog(
                isSubmitting = false,
                groupName = uiState.draft.name,
                stampLabel = uiState.draft.stamp.label,
                onDismiss = onCancelConfirmation,
                onConfirm = onConfirmCreate,
            )
        }

        is GroupCreateSubmissionState.Submitting -> {
            CreateConfirmationDialog(
                isSubmitting = true,
                groupName = uiState.draft.name,
                stampLabel = uiState.draft.stamp.label,
                onDismiss = {},
                onConfirm = {},
            )
        }

        is GroupCreateSubmissionState.Failed -> {
            when (submission.reason) {
                GroupCreateFailure.OutcomeUnknown -> {
                    if (uiState.isRecoveryDialogVisible) {
                        CreateOutcomeUnknownDialog(
                            recovery = uiState.recovery,
                            onDismiss = onDismissFailure,
                            onCheckGroups = onCheckGroupsAfterUnknownOutcome,
                            onSelectCandidate = onSelectRecoveryCandidate,
                            onRetryCreate = onRetryUnknownCreation,
                        )
                    }
                }

                GroupCreateFailure.Unavailable,
                GroupCreateFailure.InvalidInput,
                GroupCreateFailure.RateLimited,
                -> {
                    CreateFailureDialog(
                        failure = submission.reason,
                        onDismiss = onDismissFailure,
                        onRetry = onRetryFailure,
                    )
                }
            }
        }

        GroupCreateSubmissionState.Editing,
        is GroupCreateSubmissionState.Succeeded,
        GroupCreateSubmissionState.Acknowledged,
        -> {}
    }
}
