package com.pheeeew.data.press.race

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.press.time.PressEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressHttpInputMeasurementsTest {
    @Test
    fun actualBatchAttemptsAndReconciliationOutcomesShareOnlyTheSanitizedSampleNumber() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        val probe = PressHttpInputMeasurements(measurements)
        val occurredAt = 1_800_000_000_000L
        val batch = PressBatch(mapOf(EmotionState.FRUSTRATED to 2), PressEpochMillis(occurredAt))

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)

        time = 100L
        probe.firstSend(batch)
        time = 112L
        probe.attemptResult(batch, PressRaceHttpAttemptResult.NotSent)
        time = 200L
        probe.firstSend(batch)
        time = 230L
        probe.attemptResult(batch, PressRaceHttpAttemptResult.RetryAfter, retryAfterMillis = 500L)
        time = 800L
        probe.firstSend(batch)
        time = 850L
        probe.attemptResult(batch, PressRaceHttpAttemptResult.OutcomeUnknown)
        time = 900L
        probe.recoveryResult(batch, PressRaceRecoveryOutcome.Pending)
        time = 1_000L
        probe.recoveryResult(batch, PressRaceRecoveryOutcome.Retired)

        val attempts = measurements.samples.filter { it.kind == PressRaceMeasureKind.SendAttemptResult }
        assertEquals(listOf(1, 2, 3), attempts.map { it.attempt })
        assertEquals(
            listOf(
                PressRaceHttpAttemptResult.NotSent,
                PressRaceHttpAttemptResult.RetryAfter,
                PressRaceHttpAttemptResult.OutcomeUnknown,
            ),
            attempts.map { it.attemptResult },
        )
        assertEquals(listOf(12L, 30L, 50L), attempts.map { it.millis })
        assertEquals(listOf(null, 500L, null), attempts.map { it.retryAfterMillis })

        val recovery = measurements.samples.filter { it.kind == PressRaceMeasureKind.RecoveryResult }
        assertEquals(
            listOf(PressRaceRecoveryOutcome.Pending, PressRaceRecoveryOutcome.Retired),
            recovery.map { it.recoveryOutcome },
        )
        assertEquals(listOf(800L, 900L), recovery.map { it.millis })
        assertEquals(1, recovery.map { it.sample }.distinct().size)
        assertTrue(measurements.samples.all { it.source == PressRaceMeasureSource.Http })
        assertEquals(0, measurements.pendingInputCount)
    }

    @Test
    fun actualBatchCorrelatesHttpTapTimesToFirstAttemptAndFinalResponse() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        val probe = PressHttpInputMeasurements(measurements)
        val occurredAt = 1_800_000_000_000L

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        time = 15L
        probe.tap(EmotionState.IRRITATED, occurredAt)
        time = 20L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        val batch =
            PressBatch(
                mapOf(EmotionState.FRUSTRATED to 2, EmotionState.IRRITATED to 1),
                PressEpochMillis(occurredAt),
            )

        probe.batch(batch)
        time = 100L
        probe.firstSend(batch)
        time = 140L
        probe.finalResult(batch, "APPLIED")

        assertEquals(
            listOf(90L, 80L, 40L),
            measurements.samples.map { it.millis },
        )
        assertTrue(measurements.samples.all { it.source == PressRaceMeasureSource.Http })
        assertEquals(3, measurements.samples.first().presses)
    }

    @Test
    fun incompleteBatchMatchQuarantinesStaleTapsAndResumesAfterQueueDrain() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        val probe = PressHttpInputMeasurements(measurements)
        val occurredAt = 1_800_000_000_000L
        val batch = PressBatch(mapOf(EmotionState.FRUSTRATED to 2), PressEpochMillis(occurredAt))

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)
        probe.firstSend(batch)
        assertEquals(PressRaceMeasureKind.CoverageGap, measurements.samples.single().kind)
        assertEquals(2, measurements.samples.single().presses)

        time = 20L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)
        probe.firstSend(batch)
        probe.finalResult(batch, "REJECTED")
        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.CoverageGap })

        time = 30L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        time = 40L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)
        time = 100L
        probe.firstSend(batch)
        time = 150L
        probe.finalResult(batch, "REJECTED")

        assertEquals(
            listOf(PressRaceMeasureKind.CoverageGap, PressRaceMeasureKind.FirstTapToSend,
                PressRaceMeasureKind.LastTapToSend, PressRaceMeasureKind.FinalAck),
            measurements.samples.map { it.kind },
        )
        assertEquals(listOf(0L, 70L, 60L, 50L), measurements.samples.map { it.millis })
        assertEquals(2, measurements.samples[1].presses)
    }

    @Test
    fun overflowPausesOnlyUntilUntrackedQueuedInputsDrainThenResumesWithGapRecord() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        val probe = PressHttpInputMeasurements(measurements)
        val occurredAt = 1_800_000_000_000L
        val hundredPresses = PressBatch(mapOf(EmotionState.FRUSTRATED to 100), PressEpochMillis(occurredAt))

        repeat(10_000) { probe.tap(EmotionState.FRUSTRATED, occurredAt) }
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.CoverageGap })
        assertEquals(10_001, measurements.samples.last().presses)
        repeat(100) { probe.batch(hundredPresses) }
        probe.batch(PressBatch(mapOf(EmotionState.FRUSTRATED to 1), PressEpochMillis(occurredAt)))

        time = 20L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        val nextBatch = PressBatch(mapOf(EmotionState.FRUSTRATED to 1), PressEpochMillis(occurredAt))
        probe.batch(nextBatch)
        time = 30L
        probe.firstSend(nextBatch)
        time = 40L
        probe.finalResult(nextBatch, "APPLIED")

        val gap = measurements.samples.single { it.kind == PressRaceMeasureKind.CoverageGap }
        assertEquals(10_001, gap.presses)
        assertEquals(PressRaceMeasureSource.Http, gap.source)
        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.FinalAck })
        assertEquals(0, measurements.pendingInputCount)
    }

    @Test
    fun coverageGapIsLoggedImmediatelyEvenAfterRegularSinkLimit() {
        var time = 10L
        val logged = mutableListOf<PressRaceMeasureSample>()
        val measurements = PressRaceMeasurements(true, { time }) { sample -> logged.add(sample) }
        val occurredAt = 1_800_000_000_000L
        val probe = PressHttpInputMeasurements(measurements)

        repeat(1366) { index ->
            val id = "ws-$index"
            measurements.tap(id)
            measurements.firstSend(id)
            measurements.finalResult(id, "APPLIED")
        }
        assertEquals(4097, logged.size)
        assertEquals(PressRaceMeasureKind.SinkLimitReached, logged.last().kind)

        repeat(10_000) { probe.tap(EmotionState.FRUSTRATED, occurredAt) }
        probe.tap(EmotionState.FRUSTRATED, occurredAt)

        assertEquals(4098, logged.size)
        assertEquals(PressRaceMeasureKind.CoverageGap, logged.last().kind)
        assertEquals(10_001, logged.last().presses)
        assertEquals(PressRaceMeasureSource.Http, logged.last().source)
    }

    @Test
    fun recoveredResultIsClassifiedAndDiscardReleasesInputSlot() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        val probe = PressHttpInputMeasurements(measurements)
        val occurredAt = 1_800_000_000_000L
        val recovered = PressBatch(mapOf(EmotionState.FRUSTRATED to 1), PressEpochMillis(occurredAt))

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(recovered)
        time = 20L
        probe.firstSend(recovered)
        time = 40L
        probe.finalResult(recovered, "APPLIED", recovered = true)

        val rejected = PressBatch(mapOf(EmotionState.ANGRY to 1), PressEpochMillis(occurredAt))
        probe.tap(EmotionState.ANGRY, occurredAt)
        probe.batch(rejected)
        probe.discard(rejected)

        assertEquals(PressRaceMeasureKind.RecoveryResult, measurements.samples.last().kind)
        assertEquals(0, measurements.pendingInputCount)
        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.RecoveryResult })
        assertEquals(0, measurements.samples.count { it.kind == PressRaceMeasureKind.FinalAck })
    }

    @Test
    fun calendarConversionFailureIsContainedWithinDiagnosticCorrelation() {
        val measurements = PressRaceMeasurements(true, { 10L })
        val probe = PressHttpInputMeasurements(measurements) { error("calendar conversion") }
        val occurredAt = 1_800_000_000_000L
        val batch = PressBatch(mapOf(EmotionState.FRUSTRATED to 1), PressEpochMillis(occurredAt))

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)
        probe.firstSend(batch)
        probe.finalResult(batch, "APPLIED")

        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.CoverageGap })
        assertEquals(1, measurements.samples.single().presses)
        assertEquals(0, measurements.pendingInputCount)
    }

    @Test
    fun batchDateConversionFailureMarksGapAndDoesNotReuseItsTap() {
        var time = 10L
        var dateCalls = 0
        val measurements = PressRaceMeasurements(true, { time })
        val probe =
            PressHttpInputMeasurements(measurements) {
                dateCalls++
                if (dateCalls == 2) throw IllegalStateException("calendar conversion")
                "2026-10-27"
            }
        val occurredAt = 1_800_000_000_000L
        val batch = PressBatch(mapOf(EmotionState.FRUSTRATED to 1), PressEpochMillis(occurredAt))

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)
        probe.firstSend(batch)
        probe.finalResult(batch, "APPLIED")

        time = 20L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        val freshBatch = PressBatch(mapOf(EmotionState.FRUSTRATED to 1), PressEpochMillis(occurredAt))
        probe.batch(freshBatch)
        time = 100L
        probe.firstSend(freshBatch)
        time = 150L
        probe.finalResult(freshBatch, "APPLIED")

        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.CoverageGap })
        assertEquals(
            listOf(
                PressRaceMeasureKind.CoverageGap,
                PressRaceMeasureKind.FirstTapToSend,
                PressRaceMeasureKind.LastTapToSend,
                PressRaceMeasureKind.FinalAck,
            ),
            measurements.samples.map { it.kind },
        )
        assertEquals(listOf(0L, 80L, 80L, 50L), measurements.samples.map { it.millis })
    }

    @Test
    fun clockFailureQuarantinesUntilActualInputsDrainThenUsesFreshTapTimes() {
        var time = 10L
        var failClock = true
        val measurements =
            PressRaceMeasurements(true, {
                if (failClock) throw IllegalStateException("diagnostic clock")
                time
            })
        val probe = PressHttpInputMeasurements(measurements)
        val occurredAt = 1_800_000_000_000L
        val batch = PressBatch(mapOf(EmotionState.FRUSTRATED to 2), PressEpochMillis(occurredAt))

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        failClock = false
        time = 20L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)
        probe.firstSend(batch)
        probe.finalResult(batch, "APPLIED")
        assertEquals(PressRaceMeasureKind.CoverageGap, measurements.samples.single().kind)

        time = 30L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        time = 40L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        probe.batch(batch)
        time = 100L
        probe.firstSend(batch)
        time = 150L
        probe.finalResult(batch, "APPLIED")

        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.CoverageGap })
        assertEquals(listOf(0L, 70L, 60L, 50L), measurements.samples.map { it.millis })
    }

    @Test
    fun partialMatchWaitsForAuthoritativeQueueDrainBeforeResuming() {
        var time = 10L
        var queuedPresses = 3L
        val measurements = PressRaceMeasurements(true, { time })
        val probe =
            PressHttpInputMeasurements(
                measurements,
                pendingQueuePressCount = { queuedPresses },
            )
        val occurredAt = 1_800_000_000_000L
        val incomplete = PressBatch(mapOf(EmotionState.FRUSTRATED to 2), PressEpochMillis(occurredAt))

        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        queuedPresses = 1L // The actual batch left the queue; one same-day input remains queued.
        probe.batch(incomplete)
        assertEquals(3, measurements.samples.single().presses)

        time = 20L
        queuedPresses = 2L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        queuedPresses = 0L
        probe.batch(incomplete)
        probe.firstSend(incomplete)
        probe.finalResult(incomplete, "APPLIED")
        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.CoverageGap })

        time = 30L
        queuedPresses = 1L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        time = 40L
        queuedPresses = 2L
        probe.tap(EmotionState.FRUSTRATED, occurredAt)
        val complete = PressBatch(mapOf(EmotionState.FRUSTRATED to 2), PressEpochMillis(occurredAt))
        queuedPresses = 0L
        probe.batch(complete)
        time = 100L
        probe.firstSend(complete)
        time = 150L
        probe.finalResult(complete, "REJECTED")

        assertEquals(listOf(0L, 70L, 60L, 50L), measurements.samples.map { it.millis })
        assertEquals(2, measurements.samples[1].presses)
    }
}
