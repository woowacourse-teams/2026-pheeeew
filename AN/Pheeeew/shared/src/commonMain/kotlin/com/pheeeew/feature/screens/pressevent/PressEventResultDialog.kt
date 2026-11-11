package com.pheeeew.feature.screens.pressevent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pheeeew.core.designsystem.component.RaisedCardButton
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppFontScale
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appPrimary
import com.pheeeew.core.designsystem.theme.appSurface
import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.feature.emotion.component.formatCount

@Composable
internal fun PressEventResultDialog(
    total: Long,
    status: EventSubmissionStatus,
    result: PressEventAttemptResult?,
    onRetrySubmission: () -> Unit,
    onRetry: () -> Unit,
    onReturn: () -> Unit,
) {
    Dialog(onDismissRequest = onReturn) {
        AppFontScale {
            PressEventResultContent(total, status, result, onRetrySubmission, onRetry, onReturn)
        }
    }
}

@Composable
private fun PressEventResultContent(
    total: Long,
    status: EventSubmissionStatus,
    result: PressEventAttemptResult?,
    onRetrySubmission: () -> Unit,
    onRetry: () -> Unit,
    onReturn: () -> Unit,
) {
    Surface(
        shape = AppShapes.DetailDialog,
        color = EventPressColors.Surface,
        border = BorderStroke(AppBorders.Standard, EventPressColors.SurfaceBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "도전 완료!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = EventPressColors.Ink,
            )
            Text(
                "${formatCount(total)}회",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = EventPressColors.Ink,
            )
            val message =
                when (status) {
                    EventSubmissionStatus.Submitted -> "현재 순위 ${result?.rank ?: "—"}위"
                    EventSubmissionStatus.Failed -> "결과를 제출하지 못했어요"
                    EventSubmissionStatus.Conflict -> "이미 다른 횟수로 제출된 도전이에요"
                    EventSubmissionStatus.Rejected -> "결과 제출이 거절됐어요"
                    EventSubmissionStatus.Submitting, EventSubmissionStatus.None -> null
                }
            if (message != null) {
                Text(
                    message,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EventPressColors.Ink,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RaisedCardButton(
                    backgroundColor = MaterialTheme.colorScheme.appSurface,
                    onClick = onReturn,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(
                        "돌아가기",
                        Modifier.align(Alignment.Center),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.appInk,
                    )
                }
                RaisedCardButton(
                    backgroundColor = MaterialTheme.colorScheme.appPrimary,
                    onClick = if (status.canRetrySubmission) onRetrySubmission else onRetry,
                    enabled =
                        status.canRetrySubmission || status == EventSubmissionStatus.Submitted ||
                            status == EventSubmissionStatus.Conflict,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    if (status == EventSubmissionStatus.Submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(24.dp),
                            color = MaterialTheme.colorScheme.appInk,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            if (status.canRetrySubmission) "다시 시도" else "재도전",
                            Modifier.align(Alignment.Center),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.appInk,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "이벤트 결과 다이얼로그", widthDp = 360)
@Composable
private fun PressEventResultDialogPreview() {
    AppTheme {
        PressEventResultContent(
            total = 123L,
            status = EventSubmissionStatus.Submitted,
            result = PressEventAttemptResult("sample", 123L, 156L, 3L, "2026-11-11T03:00:00Z", false),
            onRetrySubmission = {},
            onRetry = {},
            onReturn = {},
        )
    }
}

@Preview(name = "이벤트 제출 실패", widthDp = 360)
@Composable
private fun PressEventSubmissionFailurePreview() {
    AppTheme {
        PressEventResultContent(123L, EventSubmissionStatus.Failed, null, {}, {}, {})
    }
}

@Preview(name = "이벤트 제출 거절", widthDp = 360)
@Composable
private fun PressEventSubmissionRejectedPreview() {
    AppTheme {
        PressEventResultContent(123L, EventSubmissionStatus.Rejected, null, {}, {}, {})
    }
}

@Preview(name = "이벤트 결과 제출 중", widthDp = 360)
@Composable
private fun PressEventSubmittingPreview() {
    AppTheme {
        PressEventResultContent(123L, EventSubmissionStatus.Submitting, null, {}, {}, {})
    }
}
