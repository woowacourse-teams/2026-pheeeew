package com.pheeeew.feature.screens.pressevent

import com.pheeeew.feature.emotion.model.EmotionKind
import kotlin.time.TimeSource

internal enum class PressEventPhase { Ready, Countdown, Playing, Finished }

internal data class PressEventUiState(
    val phase: PressEventPhase = PressEventPhase.Ready,
    val seconds: Int = 20,
    val counts: Map<EmotionKind, Long> = emptyMap(),
) {
    val total: Long get() = counts.values.sum()
    val isActive: Boolean get() = phase == PressEventPhase.Countdown || phase == PressEventPhase.Playing
}

internal fun monotonicPressEventClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
