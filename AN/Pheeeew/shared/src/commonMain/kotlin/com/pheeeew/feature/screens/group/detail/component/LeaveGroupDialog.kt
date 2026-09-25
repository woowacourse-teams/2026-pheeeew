package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.detail.GroupDetailOverlay
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_leave_body
import pheeeew.shared.generated.resources.group_detail_leave_cancel
import pheeeew.shared.generated.resources.group_detail_leave_confirm
import pheeeew.shared.generated.resources.group_detail_leave_error_body
import pheeeew.shared.generated.resources.group_detail_leave_error_title
import pheeeew.shared.generated.resources.group_detail_leave_failure_close
import pheeeew.shared.generated.resources.group_detail_leave_retry
import pheeeew.shared.generated.resources.group_detail_leave_title
import pheeeew.shared.generated.resources.group_detail_leave_unknown_body
import pheeeew.shared.generated.resources.group_detail_leave_unknown_reconcile
import pheeeew.shared.generated.resources.group_detail_leave_unknown_title
import pheeeew.shared.generated.resources.group_detail_leaving_body
import pheeeew.shared.generated.resources.group_detail_leaving_title

@Composable
internal fun LeaveGroupDialog(
    overlay: GroupDetailOverlay,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onResolveOutcome: () -> Unit,
) {
    val isWorking = overlay is GroupDetailOverlay.Leaving || overlay is GroupDetailOverlay.Left
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = !isWorking && overlay != GroupDetailOverlay.LeaveOutcomeUnknown,
                dismissOnClickOutside = !isWorking && overlay != GroupDetailOverlay.LeaveOutcomeUnknown,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(BorderStroke(1.5.dp, AppColors.GroupInk), RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp),
            ) {
                when {
                    isWorking -> {
                        WorkingContent()
                    }

                    overlay == GroupDetailOverlay.LeaveFailed -> {
                        Spacer(Modifier.height(30.dp))
                        DialogTitle(stringResource(Res.string.group_detail_leave_error_title))
                        DialogBody(stringResource(Res.string.group_detail_leave_error_body))
                        Spacer(Modifier.height(26.dp))
                        DetailDialogButton(
                            text = stringResource(Res.string.group_detail_leave_retry),
                            enabled = true,
                            isPrimary = true,
                            onClick = onRetry,
                        )
                        Spacer(Modifier.height(10.dp))
                        DetailDialogButton(
                            text = stringResource(Res.string.group_detail_leave_failure_close),
                            enabled = true,
                            isPrimary = false,
                            onClick = onDismiss,
                        )
                        Spacer(Modifier.height(28.dp))
                    }

                    overlay == GroupDetailOverlay.LeaveOutcomeUnknown -> {
                        Spacer(Modifier.height(30.dp))
                        DialogTitle(stringResource(Res.string.group_detail_leave_unknown_title))
                        DialogBody(stringResource(Res.string.group_detail_leave_unknown_body))
                        Spacer(Modifier.height(26.dp))
                        DetailDialogButton(
                            text = stringResource(Res.string.group_detail_leave_unknown_reconcile),
                            enabled = true,
                            isPrimary = true,
                            onClick = onResolveOutcome,
                        )
                        Spacer(Modifier.height(28.dp))
                    }

                    else -> {
                        Spacer(Modifier.height(30.dp))
                        DialogTitle(stringResource(Res.string.group_detail_leave_title))
                        DialogBody(stringResource(Res.string.group_detail_leave_body))
                        Spacer(Modifier.height(26.dp))
                        DetailDialogButton(
                            text = stringResource(Res.string.group_detail_leave_confirm),
                            enabled = true,
                            isPrimary = true,
                            onClick = onConfirm,
                        )
                        Spacer(Modifier.height(10.dp))
                        DetailDialogButton(
                            text = stringResource(Res.string.group_detail_leave_cancel),
                            enabled = true,
                            isPrimary = false,
                            onClick = onDismiss,
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RowCenteredProgress()
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.group_detail_leaving_body),
            color = AppColors.RankingSecondaryContent,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RowCenteredProgress() {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.height(23.dp), color = AppColors.GroupInk, strokeWidth = 2.dp)
        androidx.compose.foundation.layout
            .Spacer(Modifier.padding(horizontal = 8.dp))
        Text(
            text = stringResource(Res.string.group_detail_leaving_title),
            color = AppColors.GroupInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.GroupInk,
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DialogBody(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        color = AppColors.RankingSecondaryContent,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
}
