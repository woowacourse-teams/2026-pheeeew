package com.pheeeew.legacy.core.audio

import androidx.compose.runtime.Composable

interface BreathInput {
    suspend fun requestPermission(): Boolean

    fun start(
        onReady: () -> Unit,
        onStrengthChanged: (Float) -> Unit,
        onError: (BreathInputError) -> Unit,
    )

    fun stop()
}

@Composable
expect fun rememberBreathInput(): BreathInput
