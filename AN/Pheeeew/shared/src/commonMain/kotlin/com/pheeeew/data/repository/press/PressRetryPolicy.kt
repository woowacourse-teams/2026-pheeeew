package com.pheeeew.data.repository.press

import kotlin.random.Random

/** Retry timing is injectable; server Retry-After is a minimum, never shortened by jitter. */
internal class PressRetryPolicy(
    private val initialDelayMillis: Long = 1_000L,
    private val maxDelayMillis: Long = 60_000L,
    val minRequestIntervalMillis: Long = 100L,
    private val jitter: () -> Double = { Random.nextDouble() },
) {
    init {
        require(initialDelayMillis > 0 && maxDelayMillis >= initialDelayMillis)
        require(minRequestIntervalMillis > 0)
    }

    fun delayMillis(
        failures: Int,
        retryAfterMillis: Long = 0L,
    ): Long {
        var ceiling = initialDelayMillis
        repeat((failures - 1).coerceIn(0, 63)) {
            ceiling = if (ceiling > maxDelayMillis / 2) maxDelayMillis else (ceiling * 2).coerceAtMost(maxDelayMillis)
        }
        val fraction = jitter().coerceIn(0.0, 1.0)
        val randomized = (ceiling / 2 + (ceiling - ceiling / 2) * fraction).toLong().coerceAtLeast(1L)
        return maxOf(randomized, retryAfterMillis)
    }
}
