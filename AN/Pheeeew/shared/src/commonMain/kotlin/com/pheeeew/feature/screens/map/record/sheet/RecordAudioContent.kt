package com.pheeeew.feature.screens.map.record.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.audio.VoiceRecordingState
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.feature.screens.map.drawPlaybackWaveform
import com.pheeeew.feature.screens.map.record.noRippleClickable
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_mic
import pheeeew.shared.generated.resources.ic_refresh

@Composable
internal fun RecordAudioContent(
    audio: VoiceRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayback: () -> Unit,
    onClearRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelState =
        when {
            audio.recording -> AudioPanelUiState.Recording
            audio.filePath != null -> AudioPanelUiState.Completed
            else -> AudioPanelUiState.Ready
        }
    Column(modifier = modifier) {
        RecordAudioPanel(
            panelState = panelState,
            audio = audio,
            onPlayback = onPlayback,
            onClearRecording = onClearRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
        )
        audio.error?.let {
            Text(text = it, color = AppColors.RecordSheetRecording, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecordAudioPanel(
    panelState: AudioPanelUiState,
    audio: VoiceRecordingState,
    onPlayback: () -> Unit,
    onClearRecording: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(105.dp)
                .clip(AppShapes.Input)
                .border(width = 1.dp, color = AppColors.Border, shape = AppShapes.Input)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (panelState) {
            AudioPanelUiState.Ready -> {
                AudioReadyPanel(
                    audio = audio,
                    onStartRecording = onStartRecording,
                )
            }

            AudioPanelUiState.Recording -> {
                AdioRecordingPanel(
                    audio = audio,
                    onStopRecording = onStopRecording,
                )
            }

            AudioPanelUiState.Completed -> {
                AdioCompletedPanel(
                    audio = audio,
                    onPlayback = onPlayback,
                    onClearRecording = onClearRecording,
                )
            }
        }
    }
}

@Composable
private fun AudioReadyPanel(
    audio: VoiceRecordingState,
    onStartRecording: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .noRippleClickable(enabled = !audio.requestingPermission, onClick = onStartRecording),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "목소리로 남겨볼까요?",
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(2.dp, AppColors.TextPrimary, CircleShape)
                    .background(AppColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_mic),
                contentDescription = "녹음 시작",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AdioRecordingPanel(
    audio: VoiceRecordingState,
    onStopRecording: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .noRippleClickable(onClick = onStopRecording),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .background(AppColors.RecordSheetRecording, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "녹음 중",
                color = AppColors.RecordSheetRecording,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatAudioTime(audio.elapsedSeconds),
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioWaveform(
                isRecording = true,
                samples = audio.samples,
                playbackProgress = 0f,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(width = 1.dp, color = Color.Black, shape = CircleShape)
                        .background(AppColors.Surface)
                        .padding(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.RecordSheetRecording),
                )
            }
        }
    }
}

@Composable
private fun AdioCompletedPanel(
    audio: VoiceRecordingState,
    onPlayback: () -> Unit,
    onClearRecording: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .background(AppColors.TextPrimary, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text =
                    formatAudioTime(
                        if (audio.playing) {
                            (audio.playbackPositionMillis / 1000).toInt()
                        } else {
                            (audio.durationMillis / 1000).toInt().takeIf { it > 0 } ?: audio.elapsedSeconds
                        },
                    ),
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier =
                    Modifier
                        .noRippleClickable(onClick = onClearRecording),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_refresh),
                    contentDescription = "다시 녹음",
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "다시 녹음",
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackButton(
                isPlaying = audio.playing,
                onClick = onPlayback,
            )
            Spacer(modifier = Modifier.width(16.dp))
            AudioWaveform(
                samples = audio.samples,
                isRecording = false,
                playbackProgress = audio.playbackProgress(),
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
            )
        }
    }
}

@Composable
private fun AudioWaveform(
    samples: List<Float>,
    isRecording: Boolean,
    playbackProgress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val activeColor = AppColors.RecordSheetRecording
        val waveform =
            if (isRecording) {
                List((64 - samples.size).coerceAtLeast(0)) { 0f } + samples.takeLast(64)
            } else {
                samples
            }
        drawPlaybackWaveform(
            waveform = waveform,
            progress = if (isRecording) 1f else playbackProgress,
            inactiveColor = if (isRecording) activeColor else Color(0xFFA3ABA5),
            activeColor = activeColor,
        )
    }
}

private fun VoiceRecordingState.playbackProgress(): Float {
    val recordedDurationMillis = durationMillis.takeIf { it > 0 } ?: elapsedSeconds * 1000L
    if (recordedDurationMillis <= 0) return 0f
    return (playbackPositionMillis.toFloat() / recordedDurationMillis).coerceIn(0f, 1f)
}

@Composable
private fun PlaybackButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(AppColors.TextPrimary)
                .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            if (isPlaying) {
                drawRect(
                    color = AppColors.Surface,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.18f),
                    size =
                        Size(size.width * 0.16f, size.height * 0.64f),
                )
                drawRect(
                    color = AppColors.Surface,
                    topLeft = Offset(size.width * 0.56f, size.height * 0.18f),
                    size =
                        Size(size.width * 0.16f, size.height * 0.64f),
                )
            } else {
                val playPath =
                    Path().apply {
                        moveTo(size.width * 0.35f, size.height * 0.2f)
                        lineTo(size.width * 0.78f, size.height * 0.5f)
                        lineTo(size.width * 0.35f, size.height * 0.8f)
                        close()
                    }
                drawPath(path = playPath, color = AppColors.Surface)
            }
        }
    }
}

private fun formatAudioTime(totalSeconds: Int): String {
    val minutes = (totalSeconds / 60).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

private val previewSamples =
    List(128) { index ->
        val phase = index % 32
        when (phase) {
            in 0..5 -> 0.02f
            in 6..12 -> (phase - 5) / 8f
            in 13..20 -> (21 - phase) / 9f
            else -> 0.04f
        }
    }

@Composable
private fun RecordAudioPreview(audio: VoiceRecordingState) {
    RecordAudioContent(
        audio = audio,
        onStartRecording = {},
        onStopRecording = {},
        onPlayback = {},
        onClearRecording = {},
        modifier = Modifier.fillMaxWidth().background(AppColors.Surface).padding(20.dp),
    )
}

@Preview(name = "녹음 전", widthDp = 402, showBackground = true)
@Composable
private fun RecordAudioReadyPreview() {
    RecordAudioPreview(VoiceRecordingState())
}

@Preview(name = "마이크 권한 요청 중", widthDp = 402, showBackground = true)
@Composable
private fun RecordAudioPermissionPreview() {
    RecordAudioPreview(VoiceRecordingState(requestingPermission = true))
}

@Preview(name = "녹음 중", widthDp = 402, showBackground = true)
@Composable
private fun RecordAudioRecordingPreview() {
    RecordAudioPreview(VoiceRecordingState(recording = true, elapsedSeconds = 12, samples = previewSamples))
}

@Preview(name = "녹음 완료", widthDp = 402, showBackground = true)
@Composable
private fun RecordAudioCompletedPreview() {
    RecordAudioPreview(VoiceRecordingState(filePath = "preview.m4a", elapsedSeconds = 12, samples = previewSamples))
}

@Preview(name = "녹음 재생 중", widthDp = 402, showBackground = true)
@Composable
private fun RecordAudioPlayingPreview() {
    RecordAudioPreview(
        VoiceRecordingState(filePath = "preview.m4a", playing = true, elapsedSeconds = 12, samples = previewSamples),
    )
}

@Preview(name = "녹음 오류", widthDp = 402, showBackground = true)
@Composable
private fun RecordAudioErrorPreview() {
    RecordAudioPreview(VoiceRecordingState(error = "설정에서 마이크 권한을 허용해주세요"))
}

@Preview(name = "무음 녹음 중", widthDp = 402, showBackground = true)
@Composable
private fun RecordAudioSilentPreview() {
    RecordAudioPreview(VoiceRecordingState(recording = true, elapsedSeconds = 3, samples = List(64) { 0f }))
}
