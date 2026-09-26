package com.pheeeew.core.audio

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

data class VoiceRecordingState(
    val recording: Boolean = false,
    val requestingPermission: Boolean = false,
    val playing: Boolean = false,
    val elapsedSeconds: Int = 0,
    val samples: List<Float> = emptyList(),
    val filePath: String? = null,
    val error: String? = null,
)

interface VoiceRecorder {
    val state: StateFlow<VoiceRecordingState>

    fun start()

    fun stop()

    fun togglePlayback()

    fun pause()

    fun clear()

    fun release()
}

@Composable
expect fun rememberVoiceRecorder(): VoiceRecorder
