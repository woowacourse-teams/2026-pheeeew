package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EmotionPlaybackState(
    val id: Long? = null,
    val error: String? = null,
)

interface EmotionAudioPlayer {
    val state: StateFlow<EmotionPlaybackState>

    fun play(
        id: Long,
        url: String,
    )

    fun stop()
}

@Composable
fun rememberEmotionAudioPlayer(): EmotionAudioPlayer {
    val playback = rememberAudioPlayer()
    val scope = rememberCoroutineScope()
    val adapter = remember(playback, scope) { EmotionAudioPlayback(playback, scope) }
    DisposableEffect(adapter) { onDispose { adapter.release() } }
    return adapter
}

private class EmotionAudioPlayback(
    private val player: AudioPlayer,
    scope: CoroutineScope,
) : EmotionAudioPlayer {
    private val mutable = MutableStateFlow(EmotionPlaybackState())
    override val state = mutable.asStateFlow()
    private var emotionId: Long? = null
    private val observer =
        scope.launch {
            player.state.collect { playback ->
                mutable.value =
                    EmotionPlaybackState(
                        id = emotionId.takeIf { playback.playing || playback.loading },
                        error = playback.error,
                    )
            }
        }

    override fun play(
        id: Long,
        url: String,
    ) {
        emotionId = id
        player.play(url)
    }

    override fun stop() {
        emotionId = null
        player.stop()
        mutable.value = EmotionPlaybackState()
    }

    fun release() {
        observer.cancel()
        stop()
    }
}
