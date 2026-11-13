package com.pheeeew.feature.screens.map.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.appDestructiveAction
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appSurface
import com.pheeeew.core.designsystem.theme.appTextSecondary
import com.pheeeew.feature.screens.map.monitoring.rememberMonitoringForeground
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.noRippleClickable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_close
import pheeeew.shared.generated.resources.ic_more

@Composable
fun EmotionDetailDialog(
    uiModel: EmotionDetailUiModel,
    onDismiss: () -> Unit,
    onMoreClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    notice: String? = null,
    onNoticeDismiss: () -> Unit = {},
    presentationKey: Any? = null,
    monitoringVisible: Boolean = true,
    onShown: () -> Unit = {},
    onContentShown: () -> Unit = {},
) {
    AppDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val foreground = rememberMonitoringForeground()
        LaunchedEffect(presentationKey, foreground, monitoringVisible) {
            if (foreground && monitoringVisible) {
                withFrameNanos { }
                onShown()
                kotlinx.coroutines.delay(1000)
                onContentShown()
            }
        }
        Box(Modifier.fillMaxSize().noRippleClickable(true, onDismiss), contentAlignment = Alignment.Center) {
            EmotionDetailCard(
                uiModel,
                onDismiss,
                onMoreClick,
                onPlaybackClick,
                onReactionClick,
                Modifier.padding(AppSpacing.xxl.dp).pointerInput(Unit) { detectTapGestures(onTap = {}) },
            )
            Snackbar(
                message = notice,
                onDismiss = onNoticeDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(AppSpacing.lg.dp),
            )
        }
    }
}

@Composable
fun EmotionDetailCard(
    uiModel: EmotionDetailUiModel,
    onDismiss: () -> Unit,
    onMoreClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .widthIn(max = 376.dp)
            .fillMaxWidth()
            .clip(AppShapes.DetailDialog)
            .background(MaterialTheme.colorScheme.appSurface)
            .border(AppBorders.Standard, MaterialTheme.colorScheme.appInk, AppShapes.DetailDialog)
            .padding(AppSpacing.xxl.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxl.dp),
    ) {
        EmotionDetailHeader(uiModel, onDismiss)
        EmotionDetailEmotionHeader(uiModel, onMoreClick)
        EmotionDetailContentSection(uiModel, onPlaybackClick, onReactionClick)
    }
}

@Composable
private fun EmotionDetailHeader(
    uiModel: EmotionDetailUiModel,
    onDismiss: () -> Unit,
) {
    val emotionColors = MaterialTheme.appColors.emotion.feedback
    val emotionColor =
        when (uiModel.emotion) {
            EmotionTypeUiModel.FRUSTRATED -> emotionColors.blocked
            EmotionTypeUiModel.IRRITATED -> emotionColors.annoyed
            EmotionTypeUiModel.EXHAUSTED -> emotionColors.tired
            EmotionTypeUiModel.DISCOURAGED -> emotionColors.defeated
            EmotionTypeUiModel.ANGRY -> emotionColors.angry
        }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(uiModel.emotion.label),
            Modifier
                .background(emotionColor, AppShapes.Pill)
                .padding(horizontal = AppSpacing.lg.dp, vertical = AppSpacing.xs.dp),
            color = MaterialTheme.colorScheme.appInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).noRippleClickable(true, onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.ic_close),
                "닫기",
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.appInk,
            )
        }
    }
}

@Composable
private fun EmotionDetailEmotionHeader(
    uiModel: EmotionDetailUiModel,
    onMoreClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(uiModel.emotion.icon),
            stringResource(uiModel.emotion.label),
            Modifier.size(64.dp),
        )
        Column(
            Modifier.weight(1f).padding(start = AppSpacing.lg.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs.dp),
        ) {
            Text(
                uiModel.nickname,
                color = MaterialTheme.colorScheme.appInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(uiModel.createdAtLabel, color = MaterialTheme.colorScheme.appTextSecondary, fontSize = 12.sp)
        }
        Box(
            Modifier
                .align(Alignment.Top)
                .size(48.dp)
                .noRippleClickable(uiModel.actionsEnabled, onMoreClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.ic_more),
                "더보기",
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.appInk,
            )
        }
    }
}

@Composable
private fun EmotionDetailContentSection(
    uiModel: EmotionDetailUiModel,
    onPlaybackClick: () -> Unit,
    onReactionClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        EmotionDetailContent(uiModel.content, onPlaybackClick)
        EmotionDetailReactions(uiModel.reactions, onReactionClick, true)
        uiModel.reactionError?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.appDestructiveAction,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = AppSpacing.sm.dp),
            )
        }
    }
}

@Composable
private fun EmotionDetailContent(
    content: EmotionDetailContentUiModel,
    onPlaybackClick: () -> Unit,
) {
    when (content) {
        EmotionDetailContentUiModel.Empty -> {
            Unit
        }

        is EmotionDetailContentUiModel.Memo -> {
            val memoScrollState = rememberScrollState()
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp),
            ) {
                Text(
                    content.text,
                    Modifier
                        .fillMaxWidth()
                        .padding(end = AppSpacing.sm.dp)
                        .verticalScroll(memoScrollState),
                    color = MaterialTheme.colorScheme.appInk,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                )
                if (memoScrollState.maxValue > 0) {
                    EmotionDetailScrollbar(
                        scrollState = memoScrollState,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(4.dp)
                                .padding(vertical = AppSpacing.xs.copy(value = 2.dp).dp),
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.xxl.dp))
        }

        is EmotionDetailContentUiModel.Audio -> {
            EmotionAudioPlayer(content, onPlaybackClick, Modifier.fillMaxWidth())
            Spacer(Modifier.height(AppSpacing.xxl.dp))
        }
    }
}

@Composable
private fun EmotionDetailScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scrollbarInk = MaterialTheme.colorScheme.appInk
    Canvas(modifier) {
        val maxScroll = scrollState.maxValue
        if (maxScroll <= 0 || size.height <= 0f) return@Canvas

        val cornerRadius = size.width / 2f
        drawRoundRect(
            color = scrollbarInk.copy(alpha = 0.12f),
            cornerRadius = CornerRadius(cornerRadius),
        )

        val minThumbHeight = 20.dp.toPx()
        val contentHeight = size.height + maxScroll
        val thumbHeight =
            (size.height * size.height / contentHeight)
                .coerceAtLeast(minThumbHeight)
                .coerceAtMost(size.height)
        val scrollRange = size.height - thumbHeight
        val thumbTop =
            if (scrollRange == 0f) {
                0f
            } else {
                (scrollState.value.toFloat() / maxScroll * scrollRange).coerceIn(0f, scrollRange)
            }

        drawRoundRect(
            color = scrollbarInk.copy(alpha = 0.6f),
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(cornerRadius),
        )
    }
}

@Composable
private fun EmotionDetailReactions(
    reactions: List<EmotionReactionUiModel>,
    onReactionClick: (String) -> Unit,
    enabled: Boolean,
) {
    HorizontalDivider(color = MaterialTheme.appColors.map.detailDivider, thickness = AppBorders.Thin)
    Spacer(Modifier.height(AppSpacing.lg.dp))
    EmotionReactionGrid(reactions, onReactionClick, enabled, Modifier.fillMaxWidth())
}

@Preview(name = "감정 상세 · 녹음", widthDp = 424, showBackground = true)
@Composable
private fun EmotionDetailAudioPreview() {
    EmotionDetailPreview(EmotionDetailPreviewData.audio)
}

@Preview(name = "감정 상세 · 메모", widthDp = 424, showBackground = true)
@Composable
private fun EmotionDetailMemoPreview() {
    EmotionDetailPreview(EmotionDetailContentUiModel.Memo("버그 하나 고쳤더니 두 개가 생겼다.\n분명 아까는 됐는데...\n오늘은 진짜 여기까지만 해야지."))
}

@Preview(name = "감정 상세 · 내용 없음", widthDp = 424, showBackground = true)
@Composable
private fun EmotionDetailEmptyPreview() {
    EmotionDetailPreview(EmotionDetailContentUiModel.Empty)
}

@Preview(name = "감정 상세 다이얼로그", widthDp = 424, heightDp = 640)
@Composable
private fun EmotionDetailDialogPreview() {
    EmotionDetailDialog(EmotionDetailPreviewData.model(EmotionDetailContentUiModel.Empty), {}, {}, {}, {})
}

@Composable
private fun EmotionDetailPreview(content: EmotionDetailContentUiModel) {
    Box(Modifier.background(MaterialTheme.appColors.map.detailPlaceholder).padding(AppSpacing.xxl.dp)) {
        EmotionDetailCard(EmotionDetailPreviewData.model(content), {}, {}, {}, {})
    }
}
