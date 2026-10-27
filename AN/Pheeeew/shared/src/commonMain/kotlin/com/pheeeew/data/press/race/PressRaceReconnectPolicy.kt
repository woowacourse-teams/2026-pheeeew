package com.pheeeew.data.press.race

import kotlin.random.Random

/** Timing is independent of each input's original 10+10 second deadline. */
internal class PressRaceReconnectPolicy(
    private val jitter: () -> Double = { Random.nextDouble() },
    val initializationTimeoutMillis: Long = 10_000L,
) {
    init {
        require(initializationTimeoutMillis > 0L)
    }

    fun delayMillis(failures: Int): Long {
        val base = (500L * (1L shl (failures - 1).coerceIn(0, 4))).coerceAtMost(5_000L)
        return (base + (base * 0.2 * jitter().coerceIn(0.0, 1.0)).toLong()).coerceAtMost(5_000L)
    }

    fun retryable(failure: PressRaceFailure): Boolean =
        when {
            failure.kind == PressRaceFailureKind.Authentication -> {
                false
            }

            failure.httpStatus != null -> {
                failure.httpStatus == 408 || failure.httpStatus == 429 ||
                    failure.httpStatus >= 500
            }

            failure.kind == PressRaceFailureKind.Transport -> {
                true
            }

            else -> {
                failure.code in
                    setOf("SEND_TIMEOUT", "HEARTBEAT_TIMEOUT", "RESULT_QUEUE_OVERFLOW", "SNAPSHOT_SEND_FAILED")
            }
        }
}
