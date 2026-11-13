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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Move only the foreground; place this after drawing the fixed shadow. */
@Composable
internal fun Modifier.raisedPressEffect(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    restingOffset: Dp = 0.dp,
    pressedOffset: Dp = 2.dp,
    minimumPressedDurationMillis: Long = 0L,
    pressDurationMillis: Int = 0,
    releaseDurationMillis: Int = 80,
): Modifier {
    val pressOffset = remember(restingOffset) { Animatable(restingOffset.value) }
    LaunchedEffect(
        interactionSource,
        enabled,
        restingOffset,
        pressedOffset,
        minimumPressedDurationMillis,
        pressDurationMillis,
        releaseDurationMillis,
    ) {
        pressOffset.snapTo(restingOffset.value)
        if (enabled) {
            var pressStart: TimeMark? = null
            interactionSource.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressStart = TimeSource.Monotonic.markNow()
                        if (pressDurationMillis == 0) {
                            pressOffset.snapTo(pressedOffset.value)
                        } else {
                            pressOffset.animateTo(
                                targetValue = pressedOffset.value,
                                animationSpec = tween(pressDurationMillis),
                            )
                        }
                    }

                    is PressInteraction.Release -> {
                        // Finish the downward motion even when the tap ends before the animation.
                        val remainingPress = pressDurationMillis - (pressStart?.elapsedNow()?.inWholeMilliseconds ?: 0L)
                        if (remainingPress > 0) {
                            pressOffset.animateTo(
                                targetValue = pressedOffset.value,
                                animationSpec = tween(remainingPress.toInt()),
                            )
                        }
                        val remaining =
                            minimumPressedDurationMillis - (pressStart?.elapsedNow()?.inWholeMilliseconds ?: 0L)
                        if (remaining > 0) delay(remaining)
                        pressOffset.animateTo(
                            targetValue = restingOffset.value,
                            animationSpec =
                                tween(
                                    releaseDurationMillis,
                                    easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f),
                                ),
                        )
                    }

                    is PressInteraction.Cancel -> {
                        pressOffset.animateTo(
                            targetValue = restingOffset.value,
                            animationSpec =
                                tween(
                                    releaseDurationMillis,
                                    easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f),
                                ),
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
