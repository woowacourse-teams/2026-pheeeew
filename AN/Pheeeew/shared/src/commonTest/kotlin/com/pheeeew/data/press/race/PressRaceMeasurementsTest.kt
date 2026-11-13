package com.pheeeew.data.press.race

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressRaceMeasurementsTest {
    @Test fun batchFirstAndLastTapAndOnlyOneFinalResultUseOriginalSendTime() {
        var time = 0L
        val measurements = PressRaceMeasurements(true, { time })
        measurements.tap("private-id")
        time = 150
        measurements.tap("private-id")
        time = 200
        measurements.firstSend("private-id")
        time = 500
        measurements.firstSend("private-id")
        measurements.finalResult("private-id", "PENDING")
        time = 600
        measurements.finalResult("private-id", "APPLIED")
        measurements.finalResult("private-id", "APPLIED")
        assertEquals(listOf(200L, 50L, 400L), measurements.samples.map { it.millis })
        assertTrue(measurements.samples.all { it.presses == 2 })
        assertTrue(measurements.samples.none { it.toString().contains("private-id") })
    }

    @Test fun uiAndDrawAreDistinctAndNeverRepeatedByRecompositionOrRecovery() {
        var time = 0L
        val measurements = PressRaceMeasurements(true, { time }, { error("Diagnostic failures must not escape") })
        val key = PressRaceFrameKey("s", "c", "2026-10-27", PressRaceView.PRESS, "1")
        measurements.received(key, time)
        time = 4
        measurements.uiApplied(key)
        time = 16
        measurements.drawn(key)
        time = 100
        measurements.uiApplied(key)
        measurements.drawn(key)
        assertEquals(listOf(4L, 16L), measurements.samples.map { it.millis })
        assertEquals(
            listOf(PressRaceMeasureKind.SnapshotToUi, PressRaceMeasureKind.SnapshotToDraw),
            measurements.samples.map { it.kind },
        )
        measurements.tap("request")
        measurements.firstSend("request")
        time = 1_000
        measurements.finalResult("request", "APPLIED", recovered = true)
        assertEquals(PressRaceMeasureKind.RecoveryResult, measurements.samples.last().kind)
    }

    @Test fun boundedAndDisabledRecordersCannotGrowOrRecordUnknownOrCancelledInputs() {
        val disabled = PressRaceMeasurements()
        disabled.tap("id")
        disabled.firstSend("id")
        assertTrue(disabled.samples.isEmpty())
        var printed = 0
        val measurements = PressRaceMeasurements(true, { 0L }, { printed++ })
        measurements.firstSend("unknown")
        measurements.tap("cancelled")
        measurements.discard("cancelled")
        measurements.firstSend("cancelled")
        assertTrue(measurements.samples.isEmpty())
        repeat(200) { index ->
            val id = "$index"
            measurements.tap(id)
            measurements.firstSend(id)
            measurements.finalResult(id, "REJECTED")
        }
        assertEquals(128, measurements.samples.size)
        assertEquals(512, printed)
    }
}
