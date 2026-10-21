package com.pheeeew.data.repository.press

import kotlin.random.Random

/** Retry timing is injectable; server Retry-After is a minimum, never shortened by jitter. */
internal class PressRetryPolicy(
    private val initialDelayMillis: PressDelayMillis = PressDelayMillis(1_000L),
    private val maxDelayMillis: PressDelayMillis = PressDelayMillis(60_000L),
    val minRequestIntervalMillis: PressDelayMillis = PressDelayMillis(100L),
    private val jitter: () -> Double = { Random.nextDouble() },
) {
    init {
        require(initialDelayMillis.value > 0L && maxDelayMillis.value >= initialDelayMillis.value)
        require(minRequestIntervalMillis.value > 0L)
    }

    fun delayMillis(
        consecutiveFailures: Int,
        retryAfterMillis: PressDelayMillis = PressDelayMillis.Zero,
    ): PressDelayMillis {
        var ceiling = initialDelayMillis
        repeat((consecutiveFailures - 1).coerceIn(0, 63)) {
            ceiling =
                if (ceiling.value > maxDelayMillis.value / 2L) {
                    maxDelayMillis
                } else {
                    PressDelayMillis((ceiling.value * 2L).coerceAtMost(maxDelayMillis.value))
                }
        }
        val fraction = jitter().coerceIn(0.0, 1.0)
        val randomized =
            PressDelayMillis(
                (ceiling.value / 2L + (ceiling.value - ceiling.value / 2L) * fraction)
                    .toLong()
                    .coerceAtLeast(1L),
            )
        return PressDelayMillis(maxOf(randomized.value, retryAfterMillis.value))
    }
}
