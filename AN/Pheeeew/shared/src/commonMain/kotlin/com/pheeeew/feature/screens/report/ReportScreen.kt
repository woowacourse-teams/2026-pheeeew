package com.pheeeew.feature.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.report.component.ReportDescriptionInput
import com.pheeeew.feature.screens.report.component.ReportHeader
import com.pheeeew.feature.screens.report.component.ReportReasonOption
import com.pheeeew.feature.screens.report.component.ReportStampPrompt
import com.pheeeew.feature.screens.report.component.ReportSubmitButton
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.report_description_label

internal val reportReasons =
    listOf(
        "명예훼손 및 허위사실 유포",
        "사이버 괴롭힘",
        "자해/음란/폭력",
        "개인정보 유출",
        "사기/상업성 광고",
        "기타",
    )

data class ReportScreenUiState(
    val emotionStamp: DrawableResource,
    val selectedReason: String? = reportReasons.first(),
    val description: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@Composable
fun ReportScreen(
    uiState: ReportScreenUiState,
    onBack: () -> Unit,
    onReasonSelect: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSnackbarDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReportHeader(onBack = onBack, enabled = !uiState.isSubmitting)

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
            ) {
                ReportStampPrompt(emotionStamp = uiState.emotionStamp)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    reportReasons.forEach { reason ->
                        ReportReasonOption(
                            reason = reason,
                            selected = uiState.selectedReason == reason,
                            enabled = !uiState.isSubmitting,
                            onClick = { onReasonSelect(reason) },
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.report_description_label),
                    modifier = Modifier.padding(top = 20.dp),
                    color = AppColors.GroupInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                ReportDescriptionInput(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier.padding(top = 10.dp),
                )
                uiState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFFE5484D),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            ReportSubmitButton(
                enabled = uiState.selectedReason != null && !uiState.isSubmitting,
                isSubmitting = uiState.isSubmitting,
                onClick = onSubmit,
                modifier =
                    Modifier
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp)
                        .fillMaxWidth(),
            )
        }

        Snackbar(
            message = uiState.successMessage,
            onDismiss = onSnackbarDismiss,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

@Preview(name = "신고 화면")
@Composable
private fun ReportScreenPreview() {
    ReportScreen(
        uiState =
            ReportScreenUiState(
                emotionStamp = Res.drawable.ic_emotion_angry,
                selectedReason = reportReasons.first(),
            ),
        onBack = {},
        onReasonSelect = {},
        onDescriptionChange = {},
        onSubmit = {},
        onSnackbarDismiss = {},
    )
}
