package com.pheeeew.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PredictiveBackEffect(
    onProgress: suspend (progress: Float) -> Unit,
    onCompleted: () -> Unit,
    onCancelled: suspend () -> Unit,
) {
    BackHandler(onBack = onCompleted)
}
