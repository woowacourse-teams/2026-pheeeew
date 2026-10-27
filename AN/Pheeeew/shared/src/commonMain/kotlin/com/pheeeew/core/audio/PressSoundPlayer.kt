package com.pheeeew.core.audio

import androidx.compose.runtime.Composable

internal interface PressSoundPlayer {
    fun play()

    fun stop()
}

@Composable
internal expect fun rememberPressSoundPlayer(): PressSoundPlayer
