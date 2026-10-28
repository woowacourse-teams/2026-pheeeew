package com.pheeeew.core.audio

import androidx.compose.runtime.Composable

internal interface PressSoundPlayer {
    val feedbackProbe: PressFeedbackProbe? get() = null

    fun prepare() {}

    fun play()

    fun stop()
}

@Composable
internal expect fun rememberPressSoundPlayer(): PressSoundPlayer
