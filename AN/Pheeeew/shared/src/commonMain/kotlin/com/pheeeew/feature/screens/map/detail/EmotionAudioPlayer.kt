package com.pheeeew.feature.screens.map.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.map.drawPlaybackWaveform
import com.pheeeew.feature.screens.map.record.noRippleClickable

@Composable
internal fun EmotionAudioPlayer(
    audio: EmotionDetailContentUiModel.Audio,
    onPlaybackClick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .border(1.dp, AppColors.GroupInk, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(AppColors.GroupInk, CircleShape)
                    .noRippleClickable(
                        !audio.isPreparing && (audio.durationMillis != null || audio.error != null),
                        onPlaybackClick,
                    ).semantics {
                        role = Role.Button
                        contentDescription =
                            when {
                                audio.isPreparing -> "녹음 준비 중"
                                audio.error != null -> "녹음 다시 불러오기"
                                audio.isPlaying -> "녹음 정지"
                                else -> "녹음 재생"
                            }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (audio.isPreparing) {
                    CircularLoadingIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Canvas(Modifier.size(24.dp)) {
                        if (audio.isPlaying) {
                            drawRect(
                                Color.White,
                                Offset(size.width * .2f, size.height * .2f),
                                Size(size.width * .6f, size.height * .6f),
                            )
                        } else {
                            drawPath(
                                Path().apply {
                                    moveTo(size.width * .2f, 0f)
                                    lineTo(size.width * .9f, size.height / 2)
                                    lineTo(size.width * .2f, size.height)
                                    close()
                                },
                                Color.White,
                            )
                        }
                    }
                }
            }
            Canvas(Modifier.weight(1f).height(40.dp)) {
                drawPlaybackWaveform(
                    audio.waveform,
                    audio.playbackProgress(),
                    Color(0xFFA3ABA5),
                    AppColors.RecordSheetRecording,
                )
            }
            audio.timelineLabel()?.let { label ->
                Text(label, color = AppColors.GroupInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        audio.error?.let { Text(it, color = AppColors.RecordSheetRecording, fontSize = 12.sp) }
    }
}

@Preview(name = "녹음 플레이어 · 재생 전", widthDp = 326, showBackground = true)
@Composable
private fun EmotionAudioPlayerPreview() {
    EmotionAudioPlayer(EmotionDetailPreviewData.audio, {}, Modifier.fillMaxWidth())
}

@Preview(name = "녹음 플레이어 · 재생 중", widthDp = 326, showBackground = true)
@Composable
private fun EmotionAudioPlayerPlayingPreview() {
    EmotionAudioPlayer(
        EmotionDetailPreviewData.audio.copy(positionMillis = 7_000, isPlaying = true),
        {},
        Modifier.fillMaxWidth(),
    )
}

@Preview(name = "녹음 플레이어 · 준비 중", widthDp = 326, showBackground = true)
@Composable
private fun EmotionAudioPlayerPreparingPreview() {
    EmotionAudioPlayer(
        EmotionDetailPreviewData.audio.copy(durationMillis = null, waveform = emptyList(), isPreparing = true),
        {},
        Modifier.fillMaxWidth(),
    )
}

@Preview(name = "녹음 플레이어 · 불러오기 실패", widthDp = 326, showBackground = true)
@Composable
private fun EmotionAudioPlayerErrorPreview() {
    EmotionAudioPlayer(
        EmotionDetailPreviewData.audio.copy(durationMillis = null, error = "녹음을 불러올 수 없어. 다시 시도해."),
        {},
        Modifier.fillMaxWidth(),
    )
}
