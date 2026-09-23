package com.pheeeew.legacy.core.monitoring.tracker

import com.pheeeew.legacy.core.monitoring.MonitoringSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BreathCaptureTrackerTest {
    @Test
    fun shortSilentGapStaysInOneSegmentAndLongGapClosesIt() {
        val tracker = tracker()
        tracker.begin(origin(), screen = "map", startedAtElapsed = 0L)
        tracker.markReady(screen = "map", readyAtElapsed = 50L)

        tracker.recordSample(active = true, sampleElapsedMs = 0L, growth = 0f)
        tracker.recordSample(active = true, sampleElapsedMs = 100L, growth = 0.1f)
        tracker.recordSample(active = false, sampleElapsedMs = 100L, growth = 0.1f)
        tracker.recordSample(active = true, sampleElapsedMs = 50L, growth = 0.2f)
        val closed = tracker.recordSample(active = false, sampleElapsedMs = 200L, growth = 0.2f)

        assertNotNull(closed)
        assertEquals(1, closed.closedSegments.size)
        assertEquals(250L, closed.closedSegments.single().spanMs)
        assertEquals(100L, closed.closedSegments.single().activeMs)
        assertNull(closed.closedSegments.single().gapBeforeMs)

        tracker.recordSample(active = true, sampleElapsedMs = 50L, growth = 0.3f)
        tracker.recordSample(active = true, sampleElapsedMs = 100L, growth = 0.4f)
        val finished = tracker.finish(stopReason = "release")

        assertNotNull(finished)
        assertEquals(2, finished.segmentCount)
        assertEquals(1, finished.closedSegments.size)
        assertEquals(250L, finished.closedSegments.single().gapBeforeMs)
        assertEquals(100L, finished.closedSegments.single().spanMs)
    }

    @Test
    fun longSampleGapMarksPartialAndDoesNotAddMissingActiveTime() {
        val tracker = tracker()
        tracker.begin(origin(), screen = "map", startedAtElapsed = 0L)
        tracker.markReady(screen = "map", readyAtElapsed = 0L)

        tracker.recordSample(active = true, sampleElapsedMs = 0L, growth = 0f)
        tracker.recordSample(active = true, sampleElapsedMs = 100L, growth = 0.1f)
        val update = tracker.recordSample(active = true, sampleElapsedMs = 600L, growth = 0.2f)
        val finished = tracker.finish(stopReason = "background")

        assertNotNull(update)
        assertEquals(1, update.closedSegments.size)
        assertEquals(100L, update.closedSegments.single().activeMs)
        assertEquals("observation_gap", update.closedSegments.single().endReason)
        assertNotNull(finished)
        assertTrue(finished.observationPartial)
        assertEquals(2, finished.segmentCount)
        assertEquals(0L, finished.closedSegments.single().activeMs)
    }

    @Test
    fun growthStartedOnlyWhenActiveGrowthIncreases() {
        val tracker = tracker()
        tracker.begin(origin(), screen = "map", startedAtElapsed = 0L)
        tracker.markReady(screen = "map", readyAtElapsed = 0L)

        assertFalse(tracker.recordSample(true, 0L, 0f)!!.growthStarted)
        val update = tracker.recordSample(true, 16L, 0.2f)
        assertNotNull(update)
        assertTrue(update.growthStarted)
        assertEquals(0f, update.growthBefore)
        assertEquals(0.2f, update.growthAfter)
        assertFalse(tracker.recordSample(false, 16L, 0.2f)!!.growthStarted)
    }

    @Test
    fun resetForAttemptStartsCaptureIndexOverAndClearRemovesCapture() {
        val tracker = tracker()
        val first = tracker.begin(origin(), screen = "map", startedAtElapsed = 0L)
        assertEquals(1, first.captureIndex)
        tracker.clear()
        assertNull(tracker.activeCapture())

        val second = tracker.begin(origin(), screen = "map", startedAtElapsed = 1L)
        assertEquals(2, second.captureIndex)
        tracker.resetForAttempt()
        val newAttempt = tracker.begin(origin(), screen = "map", startedAtElapsed = 2L)
        assertEquals(1, newAttempt.captureIndex)
    }

    private fun tracker(): BreathCaptureTracker {
        var nextId = 0
        return BreathCaptureTracker { "capture-${++nextId}" }
    }

    private fun origin() =
        MonitoringSnapshot(
            sessionId = "visit-1",
            sighAttemptId = "attempt-1",
            screen = "map",
            state = "awaiting_breath",
        )
}
