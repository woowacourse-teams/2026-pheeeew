package com.pheeeew.feature.screens.collection.calendar.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.audio.AudioPlaybackState
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.feature.component.emotion.EmotionActionsMenu
import com.pheeeew.feature.component.emotion.EmotionActionsMenuMode
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionDayEmotionUiModel
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import com.pheeeew.feature.screens.collection.calendar.CollectionRetryState
import com.pheeeew.feature.screens.map.drawPlaybackWaveform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_empty
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated
import kotlin.time.Clock
import kotlin.time.Instant

internal fun LazyListScope.collectionDayEmotionItems(
    emotions: List<CollectionDayEmotionUiModel>,
    nickname: String,
    isLoadingEmotions: Boolean,
    isLoadingNextEmotions: Boolean,
    emotionsError: String?,
    audio: CollectionDayAudioState,
    onRetryEmotions: () -> Unit,
    onDeleteEmotion: (Long) -> Unit = {},
    onRetryNextEmotions: () -> Unit = onRetryEmotions,
) {
    if (emotionsError != null && emotions.isEmpty()) {
        item(key = "emotion-error") {
            CollectionRetryState(
                message = emotionsError,
                onRetry = onRetryEmotions,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }
    } else if (isLoadingEmotions && emotions.isEmpty()) {
        item(key = "emotion-loading") {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        }
    } else if (emotions.isEmpty()) {
        item(key = "emotion-empty") {
            Text(
                stringResource(Res.string.collection_empty),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        items(emotions, key = CollectionDayEmotionUiModel::id) { emotion ->
            CollectionDayEmotionRow(
                emotion = emotion,
                nickname = nickname,
                isAudioActive = audio.activeId == emotion.id,
                isAudioPreparing = audio.preparing && audio.activeId == emotion.id,
                audioPlaybackState = audio.playback,
                audioError = audio.error.takeIf { audio.activeId == emotion.id },
                onAudioClick = { audio.onClick(emotion.id) },
                onDeleteClick = { onDeleteEmotion(emotion.id) },
            )
        }
        if (isLoadingNextEmotions) {
            item(key = "emotion-next-loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = AppColors.Primary)
                }
            }
        } else if (emotionsError != null) {
            item(key = "emotion-next-error") {
                Text(
                    emotionsError,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onRetryNextEmotions).padding(16.dp),
                    color = AppColors.RecordSheetRecording,
                    textAlign = TextAlign.Center,
                )
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
) {
    val icon = emotion.emotion.toDrawable()
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(icon), null, Modifier.size(26.dp))
            Text(nickname, color = AppColors.GroupInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        val isAudio = emotion.contentType == CollectionEmotionContentType.AUDIO
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f, fill = isAudio)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        AppBorders.Standard,
                        Color(0xFFE8E8E8),
                        RoundedCornerShape(16.dp),
                    ).background(Color.White)
                    .combinedClickable(
                        onClick = { if (isAudio && !isAudioActive) onAudioClick() },
                        onLongClick = { menuOpen = true },
                    ).then(if (isAudio) Modifier.height(80.dp) else Modifier.padding(14.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (emotion.contentType) {
                    CollectionEmotionContentType.NONE -> {
                        Image(painterResource(icon), stringResource(emotion.emotion.label), Modifier.size(34.dp))
                    }

                    CollectionEmotionContentType.MEMO -> {
                        Text(emotion.memo.orEmpty(), color = AppColors.GroupInk, fontSize = 13.sp, lineHeight = 20.sp)
                    }

                    CollectionEmotionContentType.AUDIO -> {
                        if (isAudioActive) {
                            CollectionDayAudioPlayer(
                                playbackState = audioPlaybackState,
                                isPreparing = isAudioPreparing,
                                error = audioError,
                                onPlaybackClick = onAudioClick,
                                onLongClick = { menuOpen = true },
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
                EmotionActionsMenu(
                    mode = EmotionActionsMenuMode.Actions.takeIf { menuOpen },
                    isMine = true,
                    busy = false,
                    reactions = emptyList(),
                    onDismiss = { menuOpen = false },
                    onReact = null,
                    onBlock = null,
                    onReport = null,
                    onDelete = {
                        menuOpen = false
                        onDeleteClick()
                    },
                )
            }
            Text(
                formatCollectionEmotionTime(emotion.createdAt),
                modifier = Modifier.padding(bottom = 10.dp),
                color = AppColors.TextSecondary,
                fontSize = 10.sp,
            )
        }
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

private fun formatCollectionEmotionTime(createdAt: String): String {
    val timestamp = Instant.parse(createdAt)
    val seconds = (Clock.System.now() - timestamp).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> {
            "방금 전"
        }

        seconds < 3600 -> {
            "${seconds / 60}분 전"
        }

        seconds < 86400 -> {
            "${seconds / 3600}시간 전"
        }

        seconds < 4 * 86400 -> {
            "${seconds / 86400}일 전"
        }

        else -> {
            val kst = Instant.fromEpochMilliseconds(timestamp.toEpochMilliseconds() + 9 * 60 * 60 * 1000L)
            kst.toString().substringBefore('T').replace('-', '.')
        }
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
                emotions =
                    listOf(
                        CollectionDayEmotionUiModel(
                            id = 3,
                            createdAt = "2026-10-15T11:42:00Z",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 2,
                            createdAt = "2026-10-15T11:42:00Z",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 4,
                            createdAt = "2026-10-15T11:42:00Z",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 5,
                            createdAt = "2026-10-15T11:42:00Z",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 1,
                            createdAt = "2026-10-15T11:42:00Z",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                    ),
                nickname = "오늘도히유",
                isLoadingEmotions = false,
                isLoadingNextEmotions = false,
                emotionsError = null,
                audio = CollectionDayAudioState(null, playback, false, null) {},
                onRetryEmotions = {},
            )
        }
    }
}
