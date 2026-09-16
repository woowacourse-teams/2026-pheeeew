package com.pheeeew.core.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged

private const val CANCEL_ANIMATION_MILLIS = 200

@Composable
fun PredictiveBackContent(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }

    PredictiveBackEffect(
        onProgress = { value -> progress.snapTo(value) },
        onCompleted = onBack,
        onCancelled = { progress.animateTo(0f, tween(CANCEL_ANIMATION_MILLIS)) },
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { width = it.width }
                .graphicsLayer { translationX = progress.value * width },
    ) {
        content()
    }
}
