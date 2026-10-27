package com.pheeeew.data.press.race

import kotlin.time.TimeSource

/** Relative monotonic time, never an authentication expiry or a cross-device wall clock. */
internal fun newPressRaceClock(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}
