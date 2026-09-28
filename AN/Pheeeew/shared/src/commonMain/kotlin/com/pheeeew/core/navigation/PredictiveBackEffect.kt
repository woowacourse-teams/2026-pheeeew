package com.pheeeew.core.navigation

import androidx.compose.runtime.Composable

@Composable
expect fun PredictiveBackEffect(
    onProgress: suspend (progress: Float) -> Unit,
    onCompleted: () -> Unit,
    onCancelled: suspend () -> Unit,
)
