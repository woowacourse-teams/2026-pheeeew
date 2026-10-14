package com.pheeeew.feature.component.emotion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated

@Composable
internal fun EmotionChatItem(
    item: EmotionChatItemUiModel,
    menuMode: EmotionActionsMenuMode?,
    busy: Boolean,
    playing: Boolean,
    audioLoading: Boolean,
    focused: Boolean,
    onOpenOnMap: (() -> Unit)?,
    onOpenMenu: ((EmotionActionsMenuMode) -> Unit)?,
    onDismissMenu: () -> Unit,
    onReact: ((EmotionReactionType) -> Unit)?,
    onBlock: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onPlay: (() -> Unit)?,
) {
    val alignment = if (item.isMine) Alignment.End else Alignment.Start
    val actionsMenuMode = menuMode?.takeIf { it == EmotionActionsMenuMode.Actions }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = if (item.isMine) 16.dp else 0.dp, end = if (item.isMine) 0.dp else 16.dp),
        horizontalAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.isMine) EmotionProfile(item)
            Text(
                if (item.isMine) "${item.nickname} · 나" else item.nickname,
                color = AppColors.GroupInk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            if (item.isMine) EmotionProfile(item)
        }
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.isMine) MessageTime(item.timeLabel, Modifier.align(Alignment.Bottom).padding(bottom = 6.dp))
            Box(Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)) {
                Row(
                    Modifier
                        .then(
                            if (item.content is EmotionChatContentUiModel.Audio) Modifier.fillMaxWidth() else Modifier,
                        ).border(
                            AppBorders.Standard,
                            if (focused) AppColors.Primary else BubbleBorder,
                            RoundedCornerShape(16.dp),
                        ).background(Color.White, RoundedCornerShape(16.dp))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onOpenOnMap?.invoke() },
                            onLongClick = { onOpenMenu?.invoke(EmotionActionsMenuMode.Actions) },
                        ).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (item.isMine && item.content !is EmotionChatContentUiModel.EmotionOnly) {
                        EmotionFace(item)
                    }
                    Column(
                        if (item.content is EmotionChatContentUiModel.EmotionOnly) {
                            Modifier
                        } else {
                            Modifier.weight(1f, fill = item.content is EmotionChatContentUiModel.Audio)
                        },
                    ) {
                        when (val content = item.content) {
                            is EmotionChatContentUiModel.Text -> {
                                Text(
                                    text = content.value?.takeIf { it.isNotBlank() } ?: "메모 없이 남긴 감정",
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                )
                            }

                            EmotionChatContentUiModel.EmotionOnly -> {
                                Image(
                                    painterResource(item.emotionIcon),
                                    item.emotionDescription,
                                    Modifier.align(Alignment.CenterHorizontally).size(48.dp),
                                )
                            }

                            is EmotionChatContentUiModel.Audio -> {
                                TextButton(onClick = { onPlay?.invoke() }, enabled = !audioLoading && onPlay != null) {
                                    Text(
                                        when {
                                            audioLoading -> "녹음 불러오는 중…"
                                            playing -> "■ 재생 중지"
                                            else -> "▶ 녹음 듣기${content.durationLabel?.let { " $it" }.orEmpty()}"
                                        },
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                    if (!item.isMine && item.content !is EmotionChatContentUiModel.EmotionOnly) {
                        EmotionFace(item)
                    }
                }
                EmotionActionsMenu(
                    mode = actionsMenuMode,
                    isMine = item.isMine,
                    busy = busy,
                    reactions = item.reactions.map { it.type to it.selected },
                    onDismiss = onDismissMenu,
                    onReact = onReact,
                    onBlock = onBlock,
                    onReport = onReport,
                )
            }
            if (!item.isMine) MessageTime(item.timeLabel, Modifier.align(Alignment.Bottom).padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(4.dp))
        EmotionReactionRow(
            isMine = item.isMine,
            reactions = item.reactions,
            menuMode = menuMode,
            busy = busy,
            onReact = onReact,
            onOpenMenu = onOpenMenu,
            onDismissMenu = onDismissMenu,
            onBlock = onBlock,
            onReport = onReport,
        )
    }
}

@Composable
private fun EmotionProfile(item: EmotionChatItemUiModel) {
    if (item.stamp != null) EmotionStamp(item.stamp, 32.dp) else EmotionFace(item)
}

@Composable
private fun EmotionFace(item: EmotionChatItemUiModel) {
    Image(painterResource(item.emotionIcon), item.emotionDescription, Modifier.size(32.dp))
}

@Composable
private fun EmotionStamp(
    appearance: StampAppearanceUiModel,
    size: Dp,
) {
    val editorSize = 96.dp
    val scale = size / editorSize
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        GroupStamp(
            appearance = appearance,
            size = editorSize,
            modifier =
                Modifier.requiredSize(editorSize).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
private fun MessageTime(
    label: String,
    modifier: Modifier,
) {
    Text(label, modifier.widthIn(max = 48.dp), fontSize = 10.sp, color = Color.Gray)
}

private val BubbleBorder = Color(0xFFE8E8E8)

@Preview(name = "공통 감정 아이템 · 텍스트", widthDp = 402, showBackground = true)
@Composable
private fun EmotionChatItemTextPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmotionChatItem(
                item = sampleEmotionChatItem(),
                menuMode = null,
                busy = false,
                playing = false,
                audioLoading = false,
                focused = false,
                onOpenOnMap = null,
                onOpenMenu = {},
                onDismissMenu = {},
                onReact = {},
                onBlock = null,
                onReport = null,
                onPlay = null,
            )
            EmotionChatItem(
                item = sampleEmotionChatItem().copy(isMine = true, nickname = "나", timeLabel = "10분 전"),
                menuMode = null,
                busy = false,
                playing = false,
                audioLoading = false,
                focused = false,
                onOpenOnMap = null,
                onOpenMenu = {},
                onDismissMenu = {},
                onReact = {},
                onBlock = null,
                onReport = null,
                onPlay = null,
            )
        }
    }
}

@Preview(name = "공통 감정 아이템 · 오디오 및 감정", widthDp = 402, showBackground = true)
@Composable
private fun EmotionChatItemAudioPreview() {
    AppTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmotionChatItem(
                item = sampleEmotionChatItem().copy(content = EmotionChatContentUiModel.Audio(null)),
                menuMode = null,
                busy = false,
                playing = false,
                audioLoading = false,
                focused = false,
                onOpenOnMap = null,
                onOpenMenu = {},
                onDismissMenu = {},
                onReact = null,
                onBlock = null,
                onReport = null,
                onPlay = {},
            )
            EmotionChatItem(
                item = sampleEmotionChatItem().copy(content = EmotionChatContentUiModel.Audio(null)),
                menuMode = null,
                busy = false,
                playing = false,
                audioLoading = true,
                focused = false,
                onOpenOnMap = null,
                onOpenMenu = {},
                onDismissMenu = {},
                onReact = null,
                onBlock = null,
                onReport = null,
                onPlay = {},
            )
            EmotionChatItem(
                item = sampleEmotionChatItem().copy(content = EmotionChatContentUiModel.Audio("0:12")),
                menuMode = null,
                busy = false,
                playing = true,
                audioLoading = false,
                focused = false,
                onOpenOnMap = null,
                onOpenMenu = {},
                onDismissMenu = {},
                onReact = {},
                onBlock = null,
                onReport = null,
                onPlay = {},
            )
            EmotionChatItem(
                item = sampleEmotionChatItem().copy(content = EmotionChatContentUiModel.EmotionOnly),
                menuMode = null,
                busy = false,
                playing = false,
                audioLoading = false,
                focused = false,
                onOpenOnMap = null,
                onOpenMenu = {},
                onDismissMenu = {},
                onReact = {},
                onBlock = null,
                onReport = null,
                onPlay = null,
            )
        }
    }
}

private fun sampleEmotionChatItem() =
    EmotionChatItemUiModel(
        nickname = "잠깐 쉬는 구름",
        isMine = false,
        emotionIcon = Res.drawable.ic_emotion_frustrated,
        emotionDescription = "답답",
        timeLabel = "10분 전",
        content = EmotionChatContentUiModel.Text("오늘은 조금 지쳤지만, 잠깐 쉬어 가려고요."),
        reactions = listOf(ReactionCount(EmotionReactionType.HEART, 2, selected = true)),
        stamp = StampAppearanceUiModel("산책", StampShapeId.FLOWER, 0xFFACD9EE, 0xFF252826),
    )
