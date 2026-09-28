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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.map.monitoring.rememberMonitoringForeground
import com.pheeeew.feature.screens.map.record.noRippleClickable
import org.jetbrains.compose.resources.painterResource
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
                Modifier.padding(24.dp).pointerInput(Unit) { detectTapGestures(onTap = {}) },
            )
            Snackbar(
                message = notice,
                onDismiss = onNoticeDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
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
            .background(Color.White)
            .border(AppBorders.Standard, AppColors.GroupInk, AppShapes.DetailDialog)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        EmotionDetailGroupHeader(uiModel, onDismiss)
        EmotionDetailEmotionHeader(uiModel, onMoreClick)
        EmotionDetailContentSection(uiModel, onPlaybackClick, onReactionClick)
    }
}

@Composable
private fun EmotionDetailGroupHeader(
    uiModel: EmotionDetailUiModel,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            uiModel.stamp != null -> {
                GroupStamp(uiModel.stamp, 32.dp)
            }

            uiModel.stampText.isNotEmpty() -> {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(AppColors.Gray100, CircleShape)
                        .border(AppBorders.Standard, AppColors.GroupInk, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiModel.stampText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.GroupInk)
                }
            }
        }
        Text(
            uiModel.groupName,
            Modifier.weight(1f).padding(start = 8.dp),
            color = AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier.size(48.dp).noRippleClickable(true, onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.ic_close),
                "닫기",
                Modifier.size(24.dp),
                tint = AppColors.GroupInk,
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
            uiModel.emotion.label,
            Modifier.size(64.dp),
        )
        Column(
            Modifier.weight(1f).padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                uiModel.emotion.label,
                Modifier
                    .background(Color(0xFFF3C6D7), AppShapes.Pill)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = AppColors.GroupInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                uiModel.nickname,
                color = AppColors.GroupInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(uiModel.createdAtLabel, color = AppColors.TextSecondary, fontSize = 12.sp)
        }
        Box(
            Modifier
                .align(Alignment.Top)
                .size(48.dp)
                .noRippleClickable(uiModel.actionsEnabled, onMoreClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_more), "더보기", Modifier.size(24.dp), tint = AppColors.GroupInk)
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
            Text(message, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
                        .padding(end = 8.dp)
                        .verticalScroll(memoScrollState),
                    color = AppColors.GroupInk,
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
                                .padding(vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        is EmotionDetailContentUiModel.Audio -> {
            EmotionAudioPlayer(content, onPlaybackClick, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmotionDetailScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val maxScroll = scrollState.maxValue
        if (maxScroll <= 0 || size.height <= 0f) return@Canvas

        val cornerRadius = size.width / 2f
        drawRoundRect(
            color = AppColors.GroupInk.copy(alpha = 0.12f),
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
            color = AppColors.GroupInk.copy(alpha = 0.6f),
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
    HorizontalDivider(color = Color(0xFFE7E9E5), thickness = 1.dp)
    Spacer(Modifier.height(16.dp))
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
    Box(Modifier.background(Color(0xFFCCCCCC)).padding(24.dp)) {
        EmotionDetailCard(EmotionDetailPreviewData.model(content), {}, {}, {}, {})
    }
}
