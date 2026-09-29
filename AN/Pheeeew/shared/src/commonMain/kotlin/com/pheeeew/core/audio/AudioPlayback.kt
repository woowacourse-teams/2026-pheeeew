package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

data class AudioPlaybackState(
    val durationMillis: Long,
    val positionMillis: Long,
    val waveform: List<Float>,
    val isPlaying: Boolean,
    val error: String?,
) {
    companion object {
        val Empty = AudioPlaybackState(0, 0, emptyList(), false, null)
    }
}

interface AudioPlayback {
    val state: StateFlow<AudioPlaybackState>

    suspend fun prepare(bytes: ByteArray)

    fun togglePlayback()

    fun stop()

    fun release()
}

@Composable
expect fun rememberAudioPlayback(): AudioPlayback
