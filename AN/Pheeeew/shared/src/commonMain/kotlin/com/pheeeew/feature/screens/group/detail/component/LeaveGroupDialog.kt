package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import pheeeew.shared.generated.resources.group_detail_leave_still_member_body
import pheeeew.shared.generated.resources.group_detail_leave_still_member_title
import pheeeew.shared.generated.resources.group_detail_leave_title
import pheeeew.shared.generated.resources.group_detail_leave_unknown_body
import pheeeew.shared.generated.resources.group_detail_leave_unknown_reconcile
import pheeeew.shared.generated.resources.group_detail_leave_unknown_title
import pheeeew.shared.generated.resources.group_detail_leaving_body
import pheeeew.shared.generated.resources.group_detail_leaving_title
import pheeeew.shared.generated.resources.group_detail_owner_leave_body
import pheeeew.shared.generated.resources.group_detail_owner_leave_title

@Composable
internal fun LeaveGroupDialog(
    overlay: GroupDetailOverlay,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onResolveOutcome: () -> Unit,
) {
    val isWorking = overlay is GroupDetailOverlay.Leaving || overlay is GroupDetailOverlay.Left
    val shape = RoundedCornerShape(16.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = !isWorking,
                dismissOnClickOutside = !isWorking,
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
                        .widthIn(max = 380.dp)
                        .heightIn(min = 176.dp)
                        .shadow(elevation = 12.dp, shape = shape)
                        .clip(shape)
                        .background(Color.White)
                        .border(BorderStroke(1.dp, AppColors.GroupInk.copy(alpha = 0.72f)), shape)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                when {
                    isWorking -> {
                        WorkingContent()
                    }

                    overlay == GroupDetailOverlay.LeaveFailed -> {
                        LeaveDialogContent(
                            title = stringResource(Res.string.group_detail_leave_error_title),
                            body = stringResource(Res.string.group_detail_leave_error_body),
                            primaryText = stringResource(Res.string.group_detail_leave_retry),
                            secondaryText = stringResource(Res.string.group_detail_leave_failure_close),
                            onPrimary = onRetry,
                            onSecondary = onDismiss,
                        )
                    }

                    overlay == GroupDetailOverlay.LeaveOutcomeUnknown -> {
                        LeaveDialogContent(
                            title = stringResource(Res.string.group_detail_leave_unknown_title),
                            body = stringResource(Res.string.group_detail_leave_unknown_body),
                            primaryText = stringResource(Res.string.group_detail_leave_unknown_reconcile),
                            secondaryText = stringResource(Res.string.group_detail_leave_failure_close),
                            onPrimary = onResolveOutcome,
                            onSecondary = onDismiss,
                        )
                    }

                    overlay == GroupDetailOverlay.LeaveStillMember -> {
                        LeaveDialogContent(
                            title = stringResource(Res.string.group_detail_leave_still_member_title),
                            body = stringResource(Res.string.group_detail_leave_still_member_body),
                            primaryText = stringResource(Res.string.group_detail_leave_retry),
                            secondaryText = stringResource(Res.string.group_detail_leave_failure_close),
                            onPrimary = onRetry,
                            onSecondary = onDismiss,
                        )
                    }

                    overlay == GroupDetailOverlay.OwnerCannotLeave -> {
                        LeaveDialogContent(
                            title = stringResource(Res.string.group_detail_owner_leave_title),
                            body = stringResource(Res.string.group_detail_owner_leave_body),
                            primaryText = stringResource(Res.string.group_detail_leave_failure_close),
                            onPrimary = onDismiss,
                        )
                    }

                    else -> {
                        LeaveDialogContent(
                            title = stringResource(Res.string.group_detail_leave_title),
                            body = stringResource(Res.string.group_detail_leave_body),
                            primaryText = stringResource(Res.string.group_detail_leave_confirm),
                            secondaryText = stringResource(Res.string.group_detail_leave_cancel),
                            onPrimary = onConfirm,
                            onSecondary = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveDialogContent(
    title: String,
    body: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DialogTitle(title)
        DialogBody(body)
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (secondaryText != null && onSecondary != null) {
                DetailDialogButton(
                    text = secondaryText,
                    enabled = true,
                    isPrimary = false,
                    onClick = onSecondary,
                )
                Spacer(Modifier.width(2.dp))
            }
            DetailDialogButton(
                text = primaryText,
                enabled = true,
                isPrimary = true,
                onClick = onPrimary,
            )
        }
    }
}

@Composable
private fun WorkingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().height(198.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(Res.string.group_detail_leaving_title),
            color = AppColors.GroupInk,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
        )
        Text(
            text = stringResource(Res.string.group_detail_leaving_body),
            modifier = Modifier.padding(top = 8.dp),
            color = AppColors.RankingSecondaryContent,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.Primary),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = AppColors.GroupInk,
                strokeWidth = 1.8.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.group_detail_leaving_title),
                color = AppColors.GroupInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.GroupInk,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun DialogBody(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = AppColors.RankingSecondaryContent,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Start,
    )
}
