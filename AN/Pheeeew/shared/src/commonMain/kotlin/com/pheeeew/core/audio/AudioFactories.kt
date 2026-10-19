package com.pheeeew.core.audio

import androidx.compose.runtime.staticCompositionLocalOf

class AudioFactories(
    val voiceRecorder: VoiceRecorder.Factory,
    val audioPlayer: AudioPlayer.Factory,
    val audioPlayback: AudioPlayback.Factory,
)

val LocalAudioFactories = staticCompositionLocalOf<AudioFactories> { error("Audio factories are not installed") }
