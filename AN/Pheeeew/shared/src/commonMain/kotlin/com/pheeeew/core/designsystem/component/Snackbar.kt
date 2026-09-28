package com.pheeeew.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_error

private const val REPORT_SNACKBAR_DURATION_MILLIS = 3_000L

@Composable
fun Snackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastMessage by remember { mutableStateOf(message) }

    LaunchedEffect(message) {
        if (message != null) {
            lastMessage = message
            delay(REPORT_SNACKBAR_DURATION_MILLIS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 354.dp)
                    .height(48.dp),
            shape = RoundedCornerShape(1.dp),
            color = AppColors.Background,
            contentColor = AppColors.TextPrimary,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.TextPrimary),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .background(Color(0xFFFFE36E), CircleShape)
                            .border(1.dp, AppColors.TextPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SuccessCheckMark()
                }
                Text(
                    text = lastMessage.orEmpty(),
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SuccessCheckMark() {
    Canvas(Modifier.size(12.dp)) {
        val check =
            Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.52f)
                lineTo(size.width * 0.42f, size.height * 0.72f)
                lineTo(size.width * 0.80f, size.height * 0.30f)
            }
        drawPath(
            path = check,
            color = AppColors.GroupInk,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Preview
@Composable
private fun SnackbarPreview() {
    Snackbar(
        message = "신고가 접수되었습니다.",
        onDismiss = {},
    )
}

@Composable
fun Snackbar(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    var lastMessage by remember { mutableStateOf(message) }
    var lastError by remember { mutableStateOf(isError) }
    LaunchedEffect(message, isError) {
        if (message != null) {
            lastMessage = message
            lastError = isError
            delay(REPORT_SNACKBAR_DURATION_MILLIS)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        StatusSnackbarContent(lastMessage.orEmpty(), lastError)
    }
}

@Composable
private fun StatusSnackbarContent(
    message: String,
    isError: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().background(AppColors.GroupInk).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isError) {
            Icon(painterResource(Res.drawable.ic_error), null, Modifier.size(24.dp), tint = Color.Red)
        } else {
            Box(
                Modifier.size(24.dp).background(AppColors.RankingAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SuccessCheckMark() }
        }
        Text(message, color = Color.White, fontSize = 14.sp)
    }
}

@Preview(name = "등록 성공 스낵바", widthDp = 376)
@Composable
private fun RegistrationSnackbarPreview() {
    StatusSnackbarContent("선택한 위치에 감정을 남겼어요", false)
}

@Preview(name = "등록 실패 스낵바", widthDp = 376)
@Composable
private fun RegistrationErrorSnackbarPreview() {
    StatusSnackbarContent("감정을 등록하지 못했어요. 다시 시도해 주세요", true)
}
