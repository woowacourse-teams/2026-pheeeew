package com.pheeeew.feature.screens.map.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pheeeew.core.audio.rememberAudioPlayback
import com.pheeeew.domain.repository.audio.EmotionAudioRepository
import kotlinx.coroutines.CancellationException

@Composable
fun EmotionDetailOverlay(
    state: EmotionDetailLoadUiModel,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    audioRepository: EmotionAudioRepository,
    onReactionClick: (String) -> Unit,
) {
    when (state) {
        EmotionDetailLoadUiModel.Closed -> {
            Unit
        }

        is EmotionDetailLoadUiModel.Ready -> {
            val audio = state.detail.content as? EmotionDetailContentUiModel.Audio
            if (audio == null) {
                EmotionDetailDialog(state.detail, onDismiss, {}, {}, onReactionClick)
            } else {
                key(audio.playbackUrl) {
                    val playback = rememberAudioPlayback()
                    val playbackState by playback.state.collectAsState()
                    var preparing by remember { mutableStateOf(true) }
                    var loadError by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(playback, audio.playbackUrl) {
                        try {
                            playback.prepare(audioRepository.download(audio.playbackUrl))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            loadError = "녹음을 불러올 수 없어요. 다시 시도해주세요"
                        } finally {
                            preparing = false
                        }
                    }
                    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.stop() }
                    val error = loadError ?: playbackState.error
                    val content =
                        audio.copy(
                            durationMillis = playbackState.durationMillis.takeIf { it > 0 },
                            positionMillis = playbackState.positionMillis,
                            waveform = playbackState.waveform,
                            isPlaying = playbackState.isPlaying,
                            isPreparing = preparing,
                            error = error,
                        )
                    EmotionDetailDialog(
                        state.detail.copy(content = content),
                        onDismiss,
                        {},
                        { if (error != null) onRetry() else playback.togglePlayback() },
                        onReactionClick,
                    )
                }
            }
        }

        else -> {
            Unit
        }
    }
}

@Preview(name = "감정 상세 오버레이", widthDp = 424, heightDp = 640)
@Composable
private fun EmotionDetailOverlayPreview() {
    EmotionDetailOverlay(
        EmotionDetailLoadUiModel.Ready(EmotionDetailPreviewData.model(EmotionDetailContentUiModel.Empty)),
        {},
        {},
        EmotionAudioRepository { error("Preview does not fetch audio") },
        {},
    )
}
