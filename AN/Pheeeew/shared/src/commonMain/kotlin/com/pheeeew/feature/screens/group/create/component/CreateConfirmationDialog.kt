package com.pheeeew.feature.screens.group.create.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.create.GroupCreateFailure
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_confirm
import pheeeew.shared.generated.resources.group_create_confirm_body
import pheeeew.shared.generated.resources.group_create_confirm_cancel
import pheeeew.shared.generated.resources.group_create_confirm_group_stamp_summary
import pheeeew.shared.generated.resources.group_create_confirm_title
import pheeeew.shared.generated.resources.group_create_failure_close
import pheeeew.shared.generated.resources.group_create_failure_edit
import pheeeew.shared.generated.resources.group_create_failure_invalid_input
import pheeeew.shared.generated.resources.group_create_failure_rate_limited
import pheeeew.shared.generated.resources.group_create_failure_retry
import pheeeew.shared.generated.resources.group_create_failure_title
import pheeeew.shared.generated.resources.group_create_failure_unavailable
import pheeeew.shared.generated.resources.group_create_failure_unknown
import pheeeew.shared.generated.resources.group_create_submitting
import pheeeew.shared.generated.resources.group_create_submitting_title

private val DialogBorder = BorderStroke(1.5.dp, AppColors.GroupInk)

@Composable
internal fun CreateConfirmationDialog(
    isSubmitting: Boolean,
    groupName: String,
    stampLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title =
        stringResource(
            if (isSubmitting) Res.string.group_create_submitting_title else Res.string.group_create_confirm_title,
        )
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = !isSubmitting,
                dismissOnClickOutside = !isSubmitting,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSubmitting) {
                SubmittingProgressCard(title = title)
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .height(294.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .border(DialogBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp),
                ) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = title,
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.GroupInk,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    Res.string.group_create_confirm_group_stamp_summary,
                                    groupName,
                                    stampLabel,
                                ),
                            color = AppColors.RankingSecondaryContent,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(Res.string.group_create_confirm_body),
                            color = AppColors.RankingSecondaryContent,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    DialogPrimaryAction(
                        text = stringResource(Res.string.group_create_confirm),
                        onClick = onConfirm,
                    )
                    Spacer(Modifier.height(10.dp))
                    DialogSecondaryAction(
                        text = stringResource(Res.string.group_create_confirm_cancel),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.height(42.dp))
                }
            }
        }
    }
}

@Composable
private fun SubmittingProgressCard(title: String) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .shadow(elevation = 16.dp, shape = shape)
                .clip(shape)
                .background(Color.White)
                .border(width = 1.dp, color = Color(0xFFE6EEEA), shape = shape)
                .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFEAF7F2)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = AppColors.GroupInk,
                strokeWidth = 2.5.dp,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            color = AppColors.GroupInk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.group_create_submitting),
            color = AppColors.RankingSecondaryContent,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CreateFailureDialog(
    failure: GroupCreateFailure,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = stringResource(Res.string.group_create_failure_title)
    val messageResource =
        when (failure) {
            GroupCreateFailure.InvalidInput -> Res.string.group_create_failure_invalid_input
            GroupCreateFailure.Unavailable -> Res.string.group_create_failure_unavailable
            GroupCreateFailure.RateLimited -> Res.string.group_create_failure_rate_limited
            GroupCreateFailure.OutcomeUnknown -> Res.string.group_create_failure_unknown
        }
    val message = stringResource(messageResource)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
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
                        .height(294.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(DialogBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.GroupInk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    color = AppColors.RankingSecondaryContent,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                DialogPrimaryAction(
                    text =
                        stringResource(
                            if (failure == GroupCreateFailure.InvalidInput) {
                                Res.string.group_create_failure_edit
                            } else if (failure == GroupCreateFailure.RateLimited) {
                                Res.string.group_create_failure_close
                            } else {
                                Res.string.group_create_failure_retry
                            },
                        ),
                    onClick = if (failure == GroupCreateFailure.Unavailable) onRetry else onDismiss,
                )
                if (failure != GroupCreateFailure.RateLimited) {
                    Spacer(Modifier.height(10.dp))
                    DialogSecondaryAction(
                        text = stringResource(Res.string.group_create_failure_close),
                        onClick = onDismiss,
                    )
                }
                Spacer(Modifier.height(42.dp))
            }
        }
    }
}

@Composable
private fun DialogPrimaryAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(CircleShape)
                .background(AppColors.GroupInk)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DialogSecondaryAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppColors.RankingSecondaryContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
