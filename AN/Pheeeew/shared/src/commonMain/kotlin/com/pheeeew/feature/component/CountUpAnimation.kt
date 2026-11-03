package com.pheeeew.feature.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToLong

/** Keeps the first value intact and retargets increases from the currently displayed count. */
@Composable
internal fun rememberCountUp(
    count: Long,
    reducedMotion: Boolean,
): State<Long> {
    val displayed = remember { mutableLongStateOf(count) }
    var previousTarget by remember { mutableLongStateOf(count) }
    LaunchedEffect(count, reducedMotion) {
        val shouldAnimate = !reducedMotion && count > previousTarget
        previousTarget = count
        if (shouldAnimate) {
            val start = displayed.longValue
            val increase = count - start
            animate(0f, 1f, animationSpec = tween(300, easing = FastOutSlowInEasing)) { fraction, _ ->
                displayed.longValue = start + (increase.toDouble() * fraction).roundToLong().coerceIn(0L, increase)
            }
        }
        displayed.longValue = count
    }
    return displayed
}
