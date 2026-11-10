package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

internal class RaceAnimationClock {
    val elapsedMillis = mutableLongStateOf(0L)
    val visibleCharacters = mutableIntStateOf(0)
    val viewport = mutableStateOf<Rect?>(null)

    fun updateViewport(value: Rect) {
        if (viewport.value != value) viewport.value = value
    }

    fun registerVisible(delta: Int) {
        visibleCharacters.intValue = (visibleCharacters.intValue + delta).coerceAtLeast(0)
    }
}

internal val LocalRaceAnimationClock = compositionLocalOf<RaceAnimationClock?> { null }

@Composable
internal fun ProvideRaceAnimationClock(
    reducedMotion: Boolean,
    motionScale: Float,
    content: @Composable (RaceAnimationClock) -> Unit,
) {
    val clock = remember { RaceAnimationClock() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, lifecycle, reducedMotion, motionScale) {
        if (!reducedMotion && motionScale > 0f) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { clock.visibleCharacters.intValue > 0 }.collectLatest { anyVisible ->
                    if (anyVisible) {
                        while (true) {
                            delay((30L * motionScale).toLong().coerceAtLeast(1L))
                            clock.elapsedMillis.longValue += 30L
                        }
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalRaceAnimationClock provides clock) { content(clock) }
}

internal fun Modifier.raceAnimationViewport(clock: RaceAnimationClock): Modifier =
    onGloballyPositioned { clock.updateViewport(it.boundsInWindow()) }

internal fun raceCharacterVisible(
    characterBounds: Rect,
    viewport: Rect?,
): Boolean {
    if (viewport == null) return false
    val left = maxOf(characterBounds.left, viewport.left)
    val top = maxOf(characterBounds.top, viewport.top)
    val right = minOf(characterBounds.right, viewport.right)
    val bottom = minOf(characterBounds.bottom, viewport.bottom)
    return right > left && bottom > top
}
