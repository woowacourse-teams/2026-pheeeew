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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.audio.AudioPlaybackState
import com.pheeeew.core.audio.rememberAudioPlayback
import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.feature.screens.collection.CollectionDayEmotionUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
        val playbackErrors = remember(playback) { playback.state.map { it.error }.distinctUntilChanged() }
        val playbackError by playbackErrors.collectAsStateWithLifecycle(initialValue = null)
        var activeAudioId by remember { mutableStateOf<Long?>(null) }
        var preparedAudioId by remember { mutableStateOf<Long?>(null) }
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
            preparedAudioId = null
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
                preparedAudioId = emotion.id
                if (playback.state.value.error == null) playback.togglePlayback()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                audioError = "녹음을 불러오지 못했어요. 다시 눌러 주세요."
            } finally {
                preparingAudio = false
            }
        }

        val error = audioError ?: playbackError.takeIf { activeAudio != null && activeAudio.id == preparedAudioId }
        CollectionDayAudioState(activeAudioId, playback.state, preparingAudio, error) { id ->
            if (activeAudioId != id) {
                playback.stop()
                activeAudioId = id
                preparedAudioId = null
                audioRetry = 0
                audioError = null
            } else if (!preparingAudio) {
                if (audioError != null || (preparedAudioId == id && playback.state.value.error != null)) {
                    preparedAudioId = null
                    audioRetry++
                } else {
                    playback.togglePlayback()
                }
            }
        }
    }
