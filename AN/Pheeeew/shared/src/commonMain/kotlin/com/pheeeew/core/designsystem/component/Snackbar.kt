package com.pheeeew.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.snackbar_close
import pheeeew.shared.generated.resources.ic_error

private const val SNACKBAR_DURATION_MILLIS = 3_000L

@Composable
fun Snackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    maxLines: Int = 1,
    presentationKey: Any? = message,
    onShown: () -> Unit = {},
    durationMillis: Long? = SNACKBAR_DURATION_MILLIS,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showDismissAction: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val current = message?.let { SnackbarPresentation(it, isError, maxLines, actionLabel, showDismissAction) }
    var lastPresentation by remember { mutableStateOf(current) }
    SideEffect { if (current != null) lastPresentation = current }
    val presentation = current ?: lastPresentation
    val clickModifier = onClick?.let { Modifier.clickable(enabled = message != null, onClick = it) } ?: Modifier

    val currentOnShown by rememberUpdatedState(onShown)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message, presentationKey, durationMillis) {
        if (message != null && durationMillis != null) {
            delay(durationMillis)
            currentOnDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        LaunchedEffect(message, presentationKey) {
            if (message != null) {
                withFrameNanos { }
                currentOnShown()
            }
        }
        val content = presentation ?: return@AnimatedVisibility
        if (content.isError) {
            ErrorSnackbarContent(
                presentation = content,
                enabled = message != null,
                onAction = onAction,
                onDismiss = onDismiss,
                modifier = clickModifier,
            )
        } else {
            SuccessSnackbarContent(
                presentation = content,
                enabled = message != null,
                onAction = onAction,
                onDismiss = onDismiss,
                modifier = clickModifier,
            )
        }
    }
}

@Composable
private fun ErrorSnackbarContent(
    presentation: SnackbarPresentation,
    enabled: Boolean,
    onAction: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnackbarContentLayout(
        presentation = presentation,
        enabled = enabled,
        onAction = onAction,
        onDismiss = onDismiss,
        borderColor = AppColors.Error,
        modifier = modifier,
        icon = { SnackbarErrorIcon() },
    )
}

@Composable
private fun SuccessSnackbarContent(
    presentation: SnackbarPresentation,
    enabled: Boolean,
    onAction: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnackbarContentLayout(
        presentation = presentation,
        enabled = enabled,
        onAction = onAction,
        onDismiss = onDismiss,
        borderColor = AppColors.TextPrimary,
        modifier = modifier,
        icon = { SnackbarSuccessIcon() },
    )
}

@Composable
private fun SnackbarContentLayout(
    presentation: SnackbarPresentation,
    enabled: Boolean,
    onAction: (() -> Unit)?,
    onDismiss: () -> Unit,
    borderColor: Color,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = 354.dp)
                .heightIn(min = 48.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .then(modifier),
        shape = RoundedCornerShape(20.dp),
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary,
        border =
            androidx.compose.foundation.BorderStroke(
                AppBorders.Standard,
                borderColor,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()

            SnackbarMessage(
                message = presentation.message,
                maxLines = presentation.maxLines,
                modifier = Modifier.weight(1f),
            )
            if (presentation.actionLabel != null) {
                SnackbarActionButton(
                    label = presentation.actionLabel,
                    onAction = onAction,
                    enabled = enabled,
                )
            }
            if (presentation.showDismissAction) {
                SnackbarDismissButton(onDismiss = onDismiss, enabled = enabled)
            }
        }
    }
}

@Composable
private fun SnackbarErrorIcon() {
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(Res.drawable.ic_error), null, Modifier.size(24.dp), tint = AppColors.Error)
    }
}

@Composable
private fun SnackbarSuccessIcon() {
    Box(
        modifier =
            Modifier
                .size(18.dp)
                .background(Color(0xFFFFE36E), CircleShape)
                .border(AppBorders.Standard, AppColors.TextPrimary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        SuccessCheckMark()
    }
}

@Composable
private fun SnackbarMessage(
    message: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier,
        color = AppColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SnackbarActionButton(
    label: String,
    onAction: (() -> Unit)?,
    enabled: Boolean,
) {
    TextButton(onClick = { onAction?.invoke() }, enabled = enabled && onAction != null) {
        Text(label, color = AppColors.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun SnackbarDismissButton(
    onDismiss: () -> Unit,
    enabled: Boolean,
) {
    TextButton(onClick = onDismiss, enabled = enabled) {
        Text(stringResource(Res.string.snackbar_close), color = AppColors.TextPrimary, fontSize = 12.sp)
    }
}

private data class SnackbarPresentation(
    val message: String,
    val isError: Boolean,
    val maxLines: Int,
    val actionLabel: String?,
    val showDismissAction: Boolean,
)

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
private fun SnackbarSuccessPreview() {
    Snackbar(
        message = "신고가 접수됐어요.",
        onDismiss = {},
        durationMillis = null,
    )
}

@Preview
@Composable
private fun SnackbarErrorPreview() {
    Snackbar(
        message = "인터넷 연결이 끊겼어요. 연결 상태를 확인해주세요.",
        onDismiss = {},
        isError = true,
        maxLines = 3,
        durationMillis = null,
    )
}

@Preview
@Composable
private fun SnackbarRetryPreview() {
    Snackbar(
        message = "지도를 불러오지 못했어요. 다시 시도해 주세요.",
        onDismiss = {},
        isError = true,
        maxLines = 3,
        durationMillis = null,
        actionLabel = "다시 시도",
        onAction = {},
    )
}

@Preview
@Composable
private fun SnackbarSettingsPreview() {
    Snackbar(
        message = "현재 위치를 보려면 위치 권한을 허용해 주세요.",
        onDismiss = {},
        isError = true,
        maxLines = 3,
        durationMillis = null,
        actionLabel = "설정",
        onAction = {},
    )
}

@Preview
@Composable
private fun SnackbarRetryAndDismissPreview() {
    Snackbar(
        message = "감정을 불러오지 못했어요",
        onDismiss = {},
        isError = true,
        maxLines = 3,
        durationMillis = null,
        actionLabel = "다시 시도",
        onAction = {},
        showDismissAction = true,
    )
}
