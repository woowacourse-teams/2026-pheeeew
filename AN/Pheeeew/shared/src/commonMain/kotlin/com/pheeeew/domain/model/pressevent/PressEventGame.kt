package com.pheeeew.domain.model.pressevent

import com.pheeeew.domain.model.emotion.EmotionState

internal enum class PressEventGamePhase { Ready, Countdown, Playing, Finished }

internal data class PressEventGameSnapshot(
    val phase: PressEventGamePhase = PressEventGamePhase.Ready,
    val seconds: Int = (PressEventRules.DURATION_MILLIS / 1_000L).toInt(),
    val counts: Map<EmotionState, Int> = emptyMap(),
) {
    val total: Long get() = counts.values.sumOf { it.toLong() }
    val isActive: Boolean get() = phase == PressEventGamePhase.Countdown || phase == PressEventGamePhase.Playing
}

/** Time and input rules use domain emotions; presentation and coroutine ownership stay outside. */
internal class PressEventGame(
    private val nowMillis: () -> Long,
) {
    var state = PressEventGameSnapshot()
        private set
    var remainingMillis = PressEventRules.DURATION_MILLIS
        private set
    private var startsAt = 0L
    private var endsAt = 0L

    fun start(): Boolean {
        if (state.isActive) return false
        remainingMillis = PressEventRules.DURATION_MILLIS
        startsAt = nowMillis() + PressEventRules.COUNTDOWN_MILLIS
        endsAt = startsAt + PressEventRules.DURATION_MILLIS
        state =
            PressEventGameSnapshot(
                phase = PressEventGamePhase.Countdown,
                seconds = (PressEventRules.COUNTDOWN_MILLIS / 1_000L).toInt(),
            )
        return true
    }

    fun tick() {
        if (!state.isActive) return
        val now = nowMillis()
        remainingMillis = (endsAt - now).coerceIn(0L, PressEventRules.DURATION_MILLIS)
        state =
            when {
                now >= endsAt -> state.copy(phase = PressEventGamePhase.Finished, seconds = 0)
                now >= startsAt -> state.copy(phase = PressEventGamePhase.Playing, seconds = secondsUntil(endsAt, now))
                else -> state.copy(seconds = secondsUntil(startsAt, now))
            }
    }

    fun reset() {
        state = PressEventGameSnapshot()
        remainingMillis = PressEventRules.DURATION_MILLIS
    }

    fun press(emotion: EmotionState): Boolean {
        // A late input cannot extend the attempt even when its timer coroutine is delayed.
        tick()
        if (state.phase != PressEventGamePhase.Playing) return false
        state = state.copy(counts = state.counts + (emotion to ((state.counts[emotion] ?: 0) + 1)))
        return true
    }

    fun cancelActiveAttempt() {
        if (state.isActive) reset()
    }
}

private fun secondsUntil(
    deadline: Long,
    now: Long,
): Int = ((deadline - now + 999L) / 1_000L).toInt()
