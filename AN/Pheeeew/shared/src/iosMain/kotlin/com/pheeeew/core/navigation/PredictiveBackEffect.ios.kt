package com.pheeeew.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.PredictiveBackHandler
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
actual fun PredictiveBackEffect(
    onProgress: suspend (progress: Float) -> Unit,
    onCompleted: () -> Unit,
    onCancelled: suspend () -> Unit,
) {
    PredictiveBackHandler { progress ->
        try {
            progress.collect { event -> onProgress(event.progress) }
            onCompleted()
        } catch (e: CancellationException) {
            onCancelled()
        }
    }
}
