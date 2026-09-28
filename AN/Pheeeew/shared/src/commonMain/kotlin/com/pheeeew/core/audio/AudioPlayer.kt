package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

data class AudioPlayerState(
    val source: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val durationMillis: Long = 0,
    val positionMillis: Long = 0,
    val completed: Boolean = false,
)

/** Shared playback for a recorded local file or a remote audio URL. */
interface AudioPlayer {
    val state: StateFlow<AudioPlayerState>

    fun play(source: String)

    fun pause()

    fun stop()

    fun release()
}

@Composable
expect fun rememberAudioPlayer(): AudioPlayer
