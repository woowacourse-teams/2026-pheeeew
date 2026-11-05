package com.pheeeew.feature.screens.collection.calendar.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pheeeew.core.audio.AudioPlaybackState
import com.pheeeew.core.audio.rememberAudioPlayback
import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.feature.screens.collection.CollectionDayEmotionUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow

internal data class CollectionDayAudioState(
    val activeId: Long?,
    val playback: StateFlow<AudioPlaybackState>,
    val preparing: Boolean,
    val error: String?,
    val onClick: (Long) -> Unit,
)

@Composable
internal fun rememberCollectionDayAudio(
    sessionGeneration: Long?,
    selectedCardId: String,
    emotions: List<CollectionDayEmotionUiModel>,
    loader: CollectionAudioLoader,
): CollectionDayAudioState =
    key(sessionGeneration, selectedCardId) {
        val playback = rememberAudioPlayback()
        var activeAudioId by remember { mutableStateOf<Long?>(null) }
        var preparingAudio by remember { mutableStateOf(false) }
        var audioError by remember { mutableStateOf<String?>(null) }
        var audioRetry by remember { mutableIntStateOf(0) }
        val activeAudio =
            emotions.firstOrNull {
                it.id == activeAudioId &&
                    it.contentType == CollectionEmotionContentType.AUDIO
            }

        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            activeAudioId = null
            playback.stop()
        }
        LaunchedEffect(activeAudio?.id, audioRetry) {
            val emotion =
                activeAudio ?: run {
                    playback.stop()
                    return@LaunchedEffect
                }
            preparingAudio = true
            audioError = null
            try {
                val bytes = loader.load(emotion.id, emotion.playbackUrl, emotion.playbackExpiresAt)
                currentCoroutineContext().ensureActive()
                if (activeAudioId != emotion.id) return@LaunchedEffect
                playback.prepare(bytes)
                currentCoroutineContext().ensureActive()
                if (activeAudioId != emotion.id) return@LaunchedEffect
                audioError = playback.state.value.error
                if (audioError == null) playback.togglePlayback()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                audioError = "녹음을 불러오지 못했어요. 다시 눌러 주세요."
            } finally {
                preparingAudio = false
            }
        }

        CollectionDayAudioState(activeAudioId, playback.state, preparingAudio, audioError) { id ->
            if (activeAudioId != id) {
                playback.stop()
                activeAudioId = id
                audioRetry = 0
                audioError = null
            } else if (!preparingAudio) {
                if (audioError != null || playback.state.value.error != null) {
                    audioRetry++
                } else {
                    playback.togglePlayback()
                }
            }
        }
    }
