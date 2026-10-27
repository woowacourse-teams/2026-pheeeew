package com.pheeeew.core.audio

import kotlin.time.TimeSource

/** Debug opt-in only. Next Compose frame is not display presentation or audible onset. */
internal class PressFeedbackProbe(
    private val mode: String,
    private val report: (String) -> Unit,
) {
    private var samples = 0

    fun begin(): Sample? {
        if (samples >= 128) return null
        return Sample(++samples, TimeSource.Monotonic.markNow())
    }

    inner class Sample(
        private val id: Int,
        private val start: TimeSource.Monotonic.ValueTimeMark,
    ) {
        private var callbackMicros = 0L

        fun callbackReturned() {
            callbackMicros = start.elapsedNow().inWholeMicroseconds
        }

        fun nextFrame() {
            report(
                "[PressFeedbackProbe] mode=$mode sample=$id callbackUs=$callbackMicros nextFrameUs=${start.elapsedNow().inWholeMicroseconds}",
            )
        }
    }
}
