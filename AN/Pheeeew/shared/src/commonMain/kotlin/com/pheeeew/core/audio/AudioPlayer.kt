package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

data class AudioPlaybackState(
    val source: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Shared playback for a recorded local file or a remote audio URL. */
interface AudioPlayer {
    val state: StateFlow<AudioPlaybackState>

    fun play(source: String)

    fun pause()

    fun stop()

    fun release()
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer
