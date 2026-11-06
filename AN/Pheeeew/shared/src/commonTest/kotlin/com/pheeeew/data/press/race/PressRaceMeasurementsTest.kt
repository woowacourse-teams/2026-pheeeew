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
        val printed = mutableListOf<PressRaceMeasureSample>()
        val measurements = PressRaceMeasurements(true, { 0L }) { sample -> printed.add(sample) }
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
        assertEquals(513, printed.size)
        assertEquals(1, printed.count { it.kind == PressRaceMeasureKind.SinkLimitReached })
        assertEquals(PressRaceMeasureSource.Recorder, printed.last().source)

        measurements.coverageGap(PressRaceMeasureSource.Http, 3)
        assertEquals(514, printed.size)
        assertEquals(1, printed.count { it.kind == PressRaceMeasureKind.SinkLimitReached })
        assertEquals(1, printed.count { it.kind == PressRaceMeasureKind.CoverageGap })
        assertEquals(PressRaceMeasureSource.Http, printed.last().source)
    }

    @Test
    fun activationLatencyCompletesOnlyForPublishedBaselineInCurrentEpochAndView() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        measurements.beginViewActivation(
            PressRaceView.PRESS,
            PressRaceViewActivation(startedAtMillis = 10L),
            isReentry = false,
            epoch = 1,
        )
        val baseline = activationSnapshot(PressRaceView.PRESS)

        measurements.viewActivationSnapshotPublished(baseline, epoch = 2)
        measurements.viewActivationSnapshotPublished(baseline.copy(baseline = false), epoch = 1)
        measurements.viewActivationSnapshotPublished(baseline.copy(view = PressRaceView.ALL), epoch = 1)
        assertTrue(measurements.samples.isEmpty())

        measurements.viewActivationEpochChanged(epoch = 2)
        time = 80L
        measurements.viewActivationSnapshotPublished(baseline, epoch = 2)
        measurements.viewActivationSnapshotPublished(baseline, epoch = 2)

        assertEquals(1, measurements.samples.size)
        assertEquals(70L, measurements.samples.single().millis)
        assertEquals(PressRaceMeasureKind.FirstViewActivationToSnapshot, measurements.samples.single().kind)
        assertEquals(PressRaceMeasureSource.WebSocket, measurements.samples.single().source)
    }

    @Test
    fun cancelledActivationIsDroppedAndReentryHasSeparateKind() {
        var time = 40L
        val measurements = PressRaceMeasurements(true, { time })
        measurements.beginViewActivation(
            PressRaceView.PRESS,
            PressRaceViewActivation(startedAtMillis = 10L),
            isReentry = false,
            epoch = 1,
        )
        measurements.cancelViewActivation()
        measurements.viewActivationSnapshotPublished(activationSnapshot(PressRaceView.PRESS), epoch = 1)
        measurements.beginViewActivation(
            PressRaceView.PRESS,
            PressRaceViewActivation(startedAtMillis = 30L),
            isReentry = true,
            epoch = 2,
        )
        measurements.viewActivationSnapshotPublished(activationSnapshot(PressRaceView.PRESS), epoch = 1)
        assertTrue(measurements.samples.isEmpty())
        time = 55L
        measurements.viewActivationSnapshotPublished(activationSnapshot(PressRaceView.PRESS), epoch = 2)

        assertEquals(1, measurements.samples.size)
        assertEquals(25L, measurements.samples.single().millis)
        assertEquals(PressRaceMeasureKind.ReentryViewActivationToSnapshot, measurements.samples.single().kind)
    }

    @Test
    fun pathRestorationAndSocketLossToValidatedBaselineAreSeparateMeasurements() {
        var time = 100L
        val measurements = PressRaceMeasurements(true, { time })
        measurements.networkPathObserved(PressRaceNetworkPathState.Unavailable, time)
        time = 500L
        measurements.networkPathObserved(PressRaceNetworkPathState.Available, time)

        assertEquals(
            listOf(
                PressRaceMeasureKind.NetworkPathStatusObserved,
                PressRaceMeasureKind.NetworkPathUnavailableToAvailable,
                PressRaceMeasureKind.NetworkPathStatusObserved,
            ),
            measurements.samples.map { it.kind },
        )
        assertEquals(400L, measurements.samples[1].millis)
        assertEquals(PressRaceMeasureSource.NetworkPath, measurements.samples[1].source)

        measurements.socketLossDetected(
            PressRaceView.PRESS,
            epoch = 1,
            atMillis = time,
            networkPath = PressRaceNetworkPathState.Available,
        )
        measurements.socketRecoveryEpochChanged(epoch = 2, view = PressRaceView.PRESS)
        time = 600L
        measurements.socketRecoveryAttemptFailed(
            view = PressRaceView.PRESS,
            epoch = 2,
            atMillis = time,
            networkPath = PressRaceNetworkPathState.Available,
        )
        val baseline = activationSnapshot(PressRaceView.PRESS)
        measurements.socketRecoveryBaselinePublished(baseline, epoch = 1)
        measurements.socketRecoveryBaselinePublished(baseline.copy(baseline = false), epoch = 2)
        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.SocketLossDetected })
        assertTrue(measurements.samples.none { it.kind == PressRaceMeasureKind.SocketLossToValidatedBaseline })

        time = 900L
        measurements.socketRecoveryBaselinePublished(baseline, epoch = 2)
        measurements.socketRecoveryBaselinePublished(baseline, epoch = 2)
        val recovery = measurements.samples.last()
        assertEquals(PressRaceMeasureKind.SocketLossToValidatedBaseline, recovery.kind)
        assertEquals(400L, recovery.millis)
        assertEquals(1, recovery.failedAttempts)
        assertEquals(PressRaceNetworkPathState.Available, recovery.networkPathAtSocketLoss)
        assertEquals(PressRaceMeasureSource.WebSocket, recovery.source)
    }

    @Test
    fun recoveryMeasurementCancelsOnViewChangeAndEpochMismatchesCannotCompleteIt() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        val baseline = activationSnapshot(PressRaceView.PRESS)
        measurements.socketLossDetected(
            PressRaceView.PRESS,
            epoch = 1,
            atMillis = time,
            networkPath = PressRaceNetworkPathState.Unknown,
        )
        measurements.socketRecoveryEpochChanged(epoch = 2, view = PressRaceView.PRESS)
        time = 20L
        measurements.socketRecoveryBaselinePublished(baseline, epoch = 1)
        measurements.socketRecoveryEpochChanged(epoch = 3, view = PressRaceView.ALL)
        time = 40L
        measurements.socketRecoveryBaselinePublished(baseline, epoch = 3)

        assertEquals(1, measurements.samples.count { it.kind == PressRaceMeasureKind.SocketLossDetected })
        assertTrue(measurements.samples.none { it.kind == PressRaceMeasureKind.SocketLossToValidatedBaseline })
    }

    @Test
    fun socketRecoverySampleAdvancesSequenceForTheNextMeasurement() {
        var time = 10L
        val measurements = PressRaceMeasurements(true, { time })
        val baseline = activationSnapshot(PressRaceView.PRESS)
        measurements.socketLossDetected(
            PressRaceView.PRESS,
            epoch = 1,
            atMillis = time,
            networkPath = PressRaceNetworkPathState.Unknown,
        )
        time = 20L
        measurements.socketRecoveryBaselinePublished(baseline, epoch = 1)
        measurements.beginViewActivation(
            PressRaceView.PRESS,
            PressRaceViewActivation(startedAtMillis = 30L),
            isReentry = false,
            epoch = 1,
        )
        time = 40L
        measurements.viewActivationSnapshotPublished(baseline, epoch = 1)

        assertEquals(listOf(1L, 2L, 3L), measurements.samples.map { it.sample })
    }

    @Test
    fun throwingDiagnosticClockOmitsSamplesWithoutEscaping() {
        val measurements = PressRaceMeasurements(true, { error("diagnostic clock") })
        val key = PressRaceFrameKey("s", "c", "2026-10-27", PressRaceView.PRESS, "1")
        measurements.tap("input")
        measurements.firstSend("input")
        measurements.finalResult("input", "APPLIED")
        measurements.received(key, 10L)
        measurements.uiApplied(key)
        measurements.drawn(key)
        measurements.beginViewActivation(
            PressRaceView.PRESS,
            PressRaceViewActivation(startedAtMillis = 10L),
            isReentry = false,
            epoch = 1,
        )
        measurements.viewActivationSnapshotPublished(activationSnapshot(PressRaceView.PRESS), epoch = 1)

        assertTrue(measurements.samples.isEmpty())
    }

    private fun activationSnapshot(view: PressRaceView) =
        PressRaceSnapshot(
            serverGeneration = "server",
            connectionGeneration = "connection",
            date = "2026-10-27",
            snapshotVersion = "1",
            generatedAt = "2026-10-27T00:00:00Z",
            view = view,
            activeCount = 1,
            me = PressRaceParticipant("public", "name", 1, 1, true),
            nearby = emptyList(),
            top100 = emptyList(),
            baseline = true,
            overtakes = PressRaceOvertakes(0, 0),
        )
}
