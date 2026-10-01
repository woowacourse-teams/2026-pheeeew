package com.pheeeew.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/** Move only the foreground; place this after drawing the fixed shadow. */
@Composable
internal fun Modifier.raisedPressEffect(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    restingOffset: Dp = 0.dp,
    pressedOffset: Dp = 2.dp,
): Modifier {
    val pressOffset = remember(restingOffset) { Animatable(restingOffset.value) }
    LaunchedEffect(interactionSource, enabled, restingOffset, pressedOffset) {
        pressOffset.snapTo(restingOffset.value)
        if (enabled) {
            interactionSource.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressOffset.snapTo(pressedOffset.value)
                    }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressOffset.animateTo(
                            targetValue = restingOffset.value,
                            animationSpec = tween(80, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
                        )
                    }
                }
            }
        }
    }
    return graphicsLayer {
        translationY = if (enabled) pressOffset.value.dp.toPx() else 0f
    }
}
