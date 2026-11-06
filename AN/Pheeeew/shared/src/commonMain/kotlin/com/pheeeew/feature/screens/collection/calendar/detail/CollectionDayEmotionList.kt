package com.pheeeew.feature.screens.collection.calendar.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.audio.AudioPlaybackState
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionDayEmojiUiModel
import com.pheeeew.feature.screens.collection.CollectionDayEmotionContent
import com.pheeeew.feature.screens.collection.CollectionDayEmotionUiModel
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_day_emotions_empty
import pheeeew.shared.generated.resources.collection_day_emotions_error
import pheeeew.shared.generated.resources.emotion_chat_add_reaction
import pheeeew.shared.generated.resources.emotion_delete_action
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated
import pheeeew.shared.generated.resources.ic_plus

internal fun LazyListScope.collectionDayEmotionItems(
    content: CollectionDayEmotionContent,
    nickname: String,
    audio: CollectionDayAudioState,
    onRetryEmotions: () -> Unit,
    onDeleteEmotion: (Long) -> Unit = {},
    onRetryNextEmotions: () -> Unit = onRetryEmotions,
    onReact: (Long, EmotionReactionType) -> Unit = { _, _ -> },
    pendingReactionIds: Set<Long> = emptySet(),
) {
    when (content) {
        CollectionDayEmotionContent.Waiting -> {
            Unit
        }

        is CollectionDayEmotionContent.Error -> {
            item(key = "emotion-error") {
                EmotionListLoadError(
                    title = stringResource(Res.string.collection_day_emotions_error),
                    message = content.message,
                    hasItems = false,
                    icon = Res.drawable.ic_emotion_discouraged,
                    onRetry = onRetryEmotions,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }

        CollectionDayEmotionContent.Loading -> {
            item(key = "emotion-loading") {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            }
        }

        CollectionDayEmotionContent.Empty -> {
            item(key = "emotion-empty") {
                EmotionListLoadError(
                    title = stringResource(Res.string.collection_day_emotions_empty),
                    message = null,
                    hasItems = false,
                    onRetry = null,
                    icon = Res.drawable.ic_emotion_exhausted,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }

        is CollectionDayEmotionContent.Items -> {
            items(content.items, key = CollectionDayEmotionUiModel::id) { emotion ->
                CollectionDayEmotionRow(
                    emotion = emotion,
                    nickname = nickname,
                    isAudioActive = audio.activeId == emotion.id,
                    isAudioPreparing = audio.preparing && audio.activeId == emotion.id,
                    audioPlaybackState = audio.playback,
                    audioError = audio.error.takeIf { audio.activeId == emotion.id },
                    onAudioClick = { audio.onClick(emotion.id) },
                    onDeleteClick = { onDeleteEmotion(emotion.id) },
                    onReact = { type -> onReact(emotion.id, type) },
                    reactionBusy = emotion.id in pendingReactionIds,
                )
            }
            if (content.loadingMore) {
                item(key = "emotion-next-loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = AppColors.Primary)
                    }
                }
            } else if (content.appendError != null) {
                item(key = "emotion-next-error") {
                    EmotionListLoadError(
                        title = stringResource(Res.string.collection_day_emotions_error),
                        message = content.appendError,
                        hasItems = true,
                        onRetry = onRetryNextEmotions,
                        icon = Res.drawable.ic_emotion_discouraged,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionDayEmotionRow(
    emotion: CollectionDayEmotionUiModel,
    nickname: String,
    isAudioActive: Boolean,
    isAudioPreparing: Boolean,
    audioPlaybackState: StateFlow<AudioPlaybackState>,
    audioError: String?,
    onAudioClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReact: (EmotionReactionType) -> Unit,
    reactionBusy: Boolean,
) {
    val icon = emotion.emotion.toDrawable()
    var menu by remember { mutableStateOf<CollectionReactionPopup?>(null) }
    val openActions = { menu = CollectionReactionPopup.Actions }
    val dismissMenu = { menu = null }
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$nickname · 나", color = AppColors.GroupInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Image(painterResource(icon), stringResource(emotion.emotion.label), Modifier.size(32.dp))
        }
        val isAudio = emotion.contentType == CollectionEmotionContentType.AUDIO
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                emotion.timeLabel,
                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 6.dp).widthIn(max = 48.dp),
                color = Color.Gray,
                fontSize = 10.sp,
            )
            Box(Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)) {
                Box(
                    Modifier
                        .then(if (isAudio) Modifier.fillMaxWidth() else Modifier)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            AppBorders.Standard,
                            Color(0xFFE8E8E8),
                            RoundedCornerShape(16.dp),
                        ).background(Color.White)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (isAudio && !isAudioActive) onAudioClick() },
                            onLongClick = openActions,
                        ).then(if (isAudio) Modifier.height(80.dp) else Modifier.padding(12.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    when (emotion.contentType) {
                        CollectionEmotionContentType.NONE -> {
                            Image(painterResource(icon), stringResource(emotion.emotion.label), Modifier.size(48.dp))
                        }

                        CollectionEmotionContentType.MEMO -> {
                            Text(
                                text = emotion.memo?.takeIf { it.isNotBlank() } ?: "메모 없이 남긴 감정",
                                color = AppColors.TextPrimary,
                                fontSize = 14.sp,
                            )
                        }

                        CollectionEmotionContentType.AUDIO -> {
                            if (isAudioActive) {
                                CollectionDayAudioPlayer(
                                    playbackState = audioPlaybackState,
                                    isPreparing = isAudioPreparing,
                                    error = audioError,
                                    onPlaybackClick = onAudioClick,
                                    onLongClick = openActions,
                                )
                            } else {
                                Text(
                                    "▶ 녹음 듣기",
                                    modifier = Modifier.padding(start = 24.dp),
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
                if (menu == CollectionReactionPopup.Actions) {
                    CollectionEmotionReactionMenu(
                        busy = reactionBusy,
                        reactions = emotion.emojis,
                        onDismiss = dismissMenu,
                        onReact = onReact,
                        onDelete = {
                            dismissMenu()
                            onDeleteClick()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        CollectionEmotionReactionRow(
            reactions = emotion.emojis,
            busy = reactionBusy,
            onReact = onReact,
            pickerOpen = menu == CollectionReactionPopup.Reactions,
            onOpenPicker = { menu = CollectionReactionPopup.Reactions },
            onDismiss = dismissMenu,
        )
        if (isAudio && isAudioActive && audioError != null) {
            Text(
                audioError,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                color = AppColors.RecordSheetRecording,
                fontSize = 12.sp,
            )
        }
    }
}

private enum class CollectionReactionPopup { Actions, Reactions }

@Composable
private fun CollectionEmotionReactionRow(
    reactions: List<CollectionDayEmojiUiModel>,
    busy: Boolean,
    onReact: (EmotionReactionType) -> Unit,
    pickerOpen: Boolean,
    onOpenPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = reactions.filter { it.count > 0 || it.selected }
    val addDescription = stringResource(Res.string.emotion_chat_add_reaction)
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        visible.forEach { reaction ->
            Row(
                Modifier
                    .height(24.dp)
                    .background(if (reaction.selected) Color(0xFFFFE987) else Color(0xFFF2F2F2), RoundedCornerShape(50))
                    .clickable(enabled = !busy, role = Role.Button) { onReact(reaction.type) }
                    .semantics {
                        val selection = if (reaction.selected) "선택됨" else "선택 안 됨"
                        contentDescription = "${reaction.type.label} ${reaction.count}개, $selection"
                    }.padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(reaction.type.glyph, fontSize = 14.sp)
                Text(reaction.count.toString(), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (EmotionReactionType.entries.any { type -> visible.none { it.type == type } }) {
            Box {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(Color(0xFFF2F2F2), CircleShape)
                        .clickable(enabled = !busy, role = Role.Button, onClick = onOpenPicker)
                        .semantics { contentDescription = addDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_plus), null, Modifier.size(14.dp), tint = Color.Black)
                }
                if (pickerOpen) {
                    CollectionEmotionReactionMenu(busy, reactions, onDismiss, onReact)
                }
            }
        }
    }
}

@Composable
private fun CollectionEmotionReactionMenu(
    busy: Boolean,
    reactions: List<CollectionDayEmojiUiModel>,
    onDismiss: () -> Unit,
    onReact: (EmotionReactionType) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(296.dp),
        shape = RoundedCornerShape(0.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(8.dp)) {
            if (onDelete != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .shadow(5.dp, shape)
                        .clip(shape)
                        .background(Color.White)
                        .clickable(enabled = !busy, role = Role.Button, onClick = onDelete)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.emotion_delete_action), color = Color(0xFFFF1717), fontSize = 16.sp)
                    CollectionEmotionWarningIcon()
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(5.dp, shape)
                    .clip(shape)
                    .background(Color.White)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmotionReactionType.entries.forEach { type ->
                    val selected = reactions.any { it.type == type && it.selected }
                    val action = if (selected) "공감 완료" else "공감"
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = !busy,
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 22.dp),
                            ) {
                                onDismiss()
                                if (!selected) onReact(type)
                            }.semantics { contentDescription = "${type.label} $action" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(type.glyph, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionEmotionWarningIcon() {
    val red = Color(0xFFFF1717)
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val outline =
            Path().apply {
                moveTo(w * 0.3f, h * 0.08f)
                lineTo(w * 0.7f, h * 0.08f)
                lineTo(w * 0.92f, h * 0.3f)
                lineTo(w * 0.92f, h * 0.7f)
                lineTo(w * 0.7f, h * 0.92f)
                lineTo(w * 0.3f, h * 0.92f)
                lineTo(w * 0.08f, h * 0.7f)
                lineTo(w * 0.08f, h * 0.3f)
                close()
            }
        drawPath(outline, red, style = Stroke(width = 1.8.dp.toPx()))
        drawLine(
            red,
            start = Offset(w * 0.5f, h * 0.3f),
            end = Offset(w * 0.5f, h * 0.56f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(red, radius = 1.dp.toPx(), center = Offset(w * 0.5f, h * 0.71f))
    }
}

private val EmotionReactionType.glyph: String
    get() =
        when (this) {
            EmotionReactionType.HEART -> "❤️"
            EmotionReactionType.LAUGH -> "🤣"
            EmotionReactionType.CRY -> "😭"
            EmotionReactionType.DIZZY -> "😵‍💫"
            EmotionReactionType.RAGE -> "🤬"
            EmotionReactionType.SKULL -> "☠️"
        }

private val EmotionReactionType.label: String
    get() =
        when (this) {
            EmotionReactionType.HEART -> "하트"
            EmotionReactionType.LAUGH -> "웃음"
            EmotionReactionType.CRY -> "눈물"
            EmotionReactionType.DIZZY -> "어지러움"
            EmotionReactionType.RAGE -> "분노"
            EmotionReactionType.SKULL -> "해골"
        }

@Composable
private fun CollectionDayAudioPlayer(
    playbackState: StateFlow<AudioPlaybackState>,
    isPreparing: Boolean,
    error: String?,
    onPlaybackClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val playback by playbackState.collectAsStateWithLifecycle()
    val isPlaying = playback.isPlaying
    val duration = playback.durationMillis.coerceAtLeast(0)
    val position = playback.positionMillis.coerceIn(0, duration)
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AppColors.GroupInk)
                .combinedClickable(
                    onLongClick = onLongClick,
                    enabled = !isPreparing && (duration > 0 || error != null),
                    role = Role.Button,
                    onClick = onPlaybackClick,
                ).semantics {
                    contentDescription =
                        when {
                            isPreparing -> "녹음 준비 중"
                            error != null -> "녹음 다시 불러오기"
                            isPlaying -> "녹음 정지"
                            else -> "녹음 재생"
                        }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isPreparing) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(if (isPlaying) "■" else "▶", color = Color.White, fontSize = 20.sp)
            }
        }
        Canvas(Modifier.weight(1f).height(40.dp)) {
            drawPlaybackWaveform(playback.waveform, progress, Color(0xFFA3ABA5), AppColors.RecordSheetRecording)
        }
        if (duration > 0) {
            val seconds = (if (isPlaying) position else duration) / 1000
            val minutesLabel = (seconds / 60).toString().padStart(2, '0')
            val secondsLabel = (seconds % 60).toString().padStart(2, '0')
            Text(
                "$minutesLabel:$secondsLabel",
                color = AppColors.GroupInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun DrawScope.drawPlaybackWaveform(
    waveform: List<Float>,
    progress: Float,
    inactiveColor: Color,
    activeColor: Color,
) {
    drawWaveformBars(waveform, inactiveColor)
    if (progress > 0f) {
        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
            drawWaveformBars(waveform, activeColor)
        }
    }
}

private fun DrawScope.drawWaveformBars(
    waveform: List<Float>,
    color: Color,
) {
    if (waveform.isEmpty()) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), StrokeCap.Round)
        return
    }
    val bars = (size.width / 6.dp.toPx()).toInt().coerceIn(1, waveform.size)
    val step = size.width / bars
    repeat(bars) { index ->
        val start = index * waveform.size / bars
        val end = (index + 1) * waveform.size / bars
        val amplitude = waveform.subList(start, end).maxOrNull() ?: 0f
        val height = (size.height * amplitude.coerceIn(0f, 1f)).coerceAtLeast(3.dp.toPx())
        val x = step * (index + .5f)
        drawLine(
            color,
            Offset(x, (size.height - height) / 2),
            Offset(x, (size.height + height) / 2),
            3.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

private fun EmotionType.toDrawable() =
    when (this) {
        EmotionType.FRUSTRATED -> Res.drawable.ic_emotion_frustrated
        EmotionType.IRRITATED -> Res.drawable.ic_emotion_irritated
        EmotionType.EXHAUSTED -> Res.drawable.ic_emotion_exhausted
        EmotionType.DISCOURAGED -> Res.drawable.ic_emotion_discouraged
        EmotionType.ANGRY -> Res.drawable.ic_emotion_angry
    }

@Preview(name = "날짜 상세 · 감정 목록", widthDp = 402, showBackground = true)
@Composable
private fun CollectionDayEmotionListPreview() {
    CollectionPreviewTheme {
        val playback = remember { MutableStateFlow(AudioPlaybackState.Empty) }
        LazyColumn {
            collectionDayEmotionItems(
                content =
                    CollectionDayEmotionContent.Items(
                        listOf(
                            CollectionDayEmotionUiModel(
                                id = 3,
                                timeLabel = "방금 전",
                                emotion = EmotionType.EXHAUSTED,
                                contentType = CollectionEmotionContentType.MEMO,
                                memo = "오늘은 여기까지만.",
                                playbackUrl = null,
                                playbackExpiresAt = null,
                                emojis = listOf(CollectionDayEmojiUiModel(EmotionReactionType.HEART, 2, true)),
                            ),
                            CollectionDayEmotionUiModel(
                                id = 2,
                                timeLabel = "방금 전",
                                emotion = EmotionType.EXHAUSTED,
                                contentType = CollectionEmotionContentType.AUDIO,
                                memo = null,
                                playbackUrl = null,
                                playbackExpiresAt = null,
                            ),
                            CollectionDayEmotionUiModel(
                                id = 4,
                                timeLabel = "방금 전",
                                emotion = EmotionType.EXHAUSTED,
                                contentType = CollectionEmotionContentType.MEMO,
                                memo = "오늘은 여기까지만.",
                                playbackUrl = null,
                                playbackExpiresAt = null,
                            ),
                            CollectionDayEmotionUiModel(
                                id = 5,
                                timeLabel = "방금 전",
                                emotion = EmotionType.EXHAUSTED,
                                contentType = CollectionEmotionContentType.MEMO,
                                memo = "오늘은 여기까지만.",
                                playbackUrl = null,
                                playbackExpiresAt = null,
                            ),
                            CollectionDayEmotionUiModel(
                                id = 1,
                                timeLabel = "방금 전",
                                emotion = EmotionType.EXHAUSTED,
                                contentType = CollectionEmotionContentType.MEMO,
                                memo = "오늘은 여기까지만.",
                                playbackUrl = null,
                                playbackExpiresAt = null,
                            ),
                        ),
                    ),
                nickname = "오늘도히유",
                audio = CollectionDayAudioState(null, playback, false, null) {},
                onRetryEmotions = {},
            )
        }
    }
}
