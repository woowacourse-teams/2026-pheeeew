package com.pheeeew.legacy.feature.map.sighlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import com.pheeeew.legacy.core.navigation.PredictiveBackEffect
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_close

@Composable
internal fun SighReportScreen(
    uiState: SighModerationUiState,
    onReasonSelect: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PredictiveBackEffect(
        onProgress = {},
        onCompleted = { if (!uiState.isSubmitting) onDismiss() },
        onCancelled = {},
    )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Navy800)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "신고 사유를 알려주세요",
                style = AppTheme.typography.sectionHeader,
                color = AppColors.Cream100,
            )
            IconButton(
                onClick = onDismiss,
                enabled = !uiState.isSubmitting,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = "닫기",
                    tint = AppColors.Cream100,
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 12.dp, end = 20.dp),
        ) {
            sighReportReasons.forEach { reason ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isSubmitting) { onReasonSelect(reason) }
                            .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = uiState.selectedReason == reason,
                        onClick = { onReasonSelect(reason) },
                        enabled = !uiState.isSubmitting,
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = AppColors.Cream100,
                                unselectedColor = AppColors.Blue200,
                            ),
                    )
                    Text(
                        text = reason,
                        style = AppTheme.typography.menuItem,
                        color = AppColors.Cream100,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row {
                Text(
                    text = "상세 설명",
                    style = AppTheme.typography.menuItem.copy(fontWeight = FontWeight.Bold),
                    color = AppColors.Cream100,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "(선택)",
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            BasicTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                enabled = !uiState.isSubmitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 184.dp)
                        .background(AppColors.Navy700, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                textStyle =
                    TextStyle(
                        color = AppColors.Cream100,
                        fontSize = AppTheme.typography.dialogBody.fontSize,
                    ),
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (uiState.description.isEmpty()) {
                            Text(
                                text = "신고 내용을 자세히 작성해주세요.",
                                style = AppTheme.typography.dialogBody,
                                color = AppColors.Cream100.copy(alpha = 0.4f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "${uiState.description.length}/$MAX_REPORT_REASON_LENGTH",
                    style = AppTheme.typography.caption,
                    color = AppColors.Cream100.copy(alpha = 0.6f),
                )
            }
            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = AppTheme.typography.caption,
                    color = AppColors.Red400,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        Button(
            onClick = onSubmit,
            enabled = uiState.canSubmitReport,
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.Black900,
                    contentColor = AppColors.Cream100,
                    disabledContainerColor = AppColors.Black900.copy(alpha = 0.5f),
                    disabledContentColor = AppColors.Cream100.copy(alpha = 0.45f),
                ),
            modifier =
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .height(52.dp),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = AppColors.Cream100,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text = "확인", style = AppTheme.typography.button)
            }
        }
    }
}

@Preview
@Composable
private fun SighReportScreenPreview() {
    AppTheme {
        SighReportScreen(
            uiState =
                SighModerationUiState(
                    reportTarget = SighModerationTarget(sighId = 1L, nickname = "테스터"),
                ),
            onReasonSelect = {},
            onDescriptionChange = {},
            onSubmit = {},
            onDismiss = {},
        )
    }
}
