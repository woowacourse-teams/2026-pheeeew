package com.pheeeew.legacy.core.monitoring

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MonitoringTest {
    @Test
    fun identitySurvivesRestartAndVisitsDoNot() {
        val f = Fixture()
        val first = f.create()
        first.foreground()
        val session = first.snapshot()!!.sessionId
        val second = f.create()
        second.foreground()
        assertEquals(first.anonymousId, second.anonymousId)
        assertNotEquals(session, second.snapshot()!!.sessionId)
        assertEquals(1, f.events("app_first_opened").size)
        assertEquals("process_interrupted", f.events("app_visit_ended").single().field("reason"))
        assertEquals("returning", f.events("app_visit_started").last().field("install_class"))
    }

    @Test
    fun repeatedLifecycleSignalsAndThirtyMinuteBoundary() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        val first = m.snapshot()!!.sessionId
        m.foreground()
        m.background()
        m.background()
        f.advance(Monitoring.VISIT_TIMEOUT_MS - 1)
        m.foreground()
        assertEquals(first, m.snapshot()!!.sessionId)
        m.background()
        f.advance(Monitoring.VISIT_TIMEOUT_MS)
        m.foreground()
        assertNotEquals(first, m.snapshot()!!.sessionId)
        assertEquals(2, f.events("app_visit_started").size)
        assertEquals(2, f.events("app_backgrounded").size)
    }

    @Test
    fun clockChangesDoNotExpireVisits() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        val session = m.snapshot()!!.sessionId
        m.background()
        f.wall += 100_000_000
        m.foreground()
        assertEquals(session, m.snapshot()!!.sessionId)
    }

    @Test
    fun backgroundFlushesEventsAcceptedByTheSdk() {
        val f = Fixture()
        val m = f.create()
        m.foreground()

        m.background()

        assertEquals(2, f.transport.flushes)
        assertEquals(1, f.events("app_backgrounded").size)
    }

    @Test
    fun foregroundFlushesSdkEventsLeftFromAnEarlierSuspension() {
        val f = Fixture()
        val m = f.create()

        m.foreground()

        assertEquals(1, f.transport.flushes)
    }

    @Test
    fun midnightUsesSeoulDateAndBackgroundIsNotActivity() {
        val f = Fixture()
        f.wall = Instant.parse("2026-09-17T14:59:59Z").toEpochMilliseconds()
        val m = f.create()
        m.foreground()
        val session = m.snapshot()!!.sessionId
        f.advance(2000)
        m.tick()
        assertEquals(listOf("2026-09-17", "2026-09-18"), f.events("app_active_day").map { it.field("activity_date") })
        assertEquals(session, m.snapshot()!!.sessionId)
        m.background()
        f.advance(86_400_000)
        m.tick()
        assertEquals(2, f.events("app_active_day").size)
    }

    @Test
    fun clockRollbackDoesNotDuplicateActiveDay() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        f.advance(86_400_000)
        m.tick()
        f.wall -= 86_400_000
        m.tick()
        assertEquals(2, f.events("app_active_day").size)
    }

    @Test
    fun captureAndSaveRetriesKeepAttemptButChangeChildIds() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        assertTrue(m.beginAttempt(false))
        assertFalse(m.beginAttempt(false))
        val capture1 = m.beginCapture()!!
        val capture2 = m.beginCapture()!!
        assertEquals(capture1.sighAttemptId, capture2.sighAttemptId)
        assertNotEquals(capture1.captureId, capture2.captureId)
        assertEquals(2, capture2.captureIndex)
        val save1 = m.beginSave()!!
        assertEquals(save1, m.beginSave())
        m.saveResult(save1, false, 100)
        val save2 = m.beginSave()!!
        assertNotEquals(save1.saveAttemptId, save2.saveAttemptId)
        assertEquals(save1.sighAttemptId, save2.sighAttemptId)
        m.saveResult(save2, true, 200)
        assertEquals(listOf("1", "2"), f.events("save_started").map { it.field("save_index") })
        assertEquals(1, f.events("sigh_started").size)
        assertEquals(1, f.events("sigh_attempt_ended").size)
    }

    @Test
    fun duplicateResultsAndCancellationAreIdempotent() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        val save = m.beginSave()!!
        m.saveResult(save, true, 100)
        m.saveResult(save, true, 100)
        m.endAttempt()
        assertEquals(1, f.events("save_result").size)
        assertEquals(1, f.events("first_sigh_saved").size)
        assertEquals(1, f.events("sigh_attempt_ended").size)
        assertNull(m.snapshot()!!.sighAttemptId)
    }

    @Test
    fun lateResultUsesOriginAndCannotClearNewAttempt() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        val old = m.beginSave()!!
        m.endAttempt("unknown", "process_interrupted")
        m.background()
        f.advance(Monitoring.VISIT_TIMEOUT_MS)
        m.foreground()
        m.beginAttempt(false)
        val current = m.snapshot()!!
        m.setScreen("settings")
        m.saveResult(old, true, 10)
        assertEquals(old.sessionId, f.events("save_result").single().field("session_id"))
        assertEquals(old.screen, f.events("save_result").single().field("screen"))
        assertEquals(current.sighAttemptId, m.snapshot()!!.sighAttemptId)
        assertEquals(1, f.events("sigh_attempt_ended").size)
    }

    @Test
    fun handleFromAnotherMonitoringInstanceIsIgnored() {
        val firstFixture = Fixture()
        val first = firstFixture.create()
        first.foreground()
        first.beginAttempt(false)
        val foreignHandle = first.beginSave()!!

        val secondFixture = Fixture()
        val second = secondFixture.create()
        second.foreground()
        second.beginAttempt(false)
        second.saveResult(foreignHandle, true, 100)

        assertEquals(0, secondFixture.events("save_result").size)
    }

    @Test
    fun pendingSaveAtVisitEndIsUnknownAndLateSuccessDoesNotDuplicateEnd() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        val save = m.beginSave()!!
        m.background()
        f.advance(Monitoring.VISIT_TIMEOUT_MS)
        m.foreground()
        assertEquals("unknown", f.events("sigh_attempt_ended").single().field("outcome"))
        m.saveResult(save, true, 100)
        assertEquals(1, f.events("sigh_attempt_ended").size)
        assertEquals("success", f.events("save_result").single().field("outcome"))
    }

    @Test
    fun processInterruptionRestoresUnknownPendingSave() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.beginSave()
        f.create().foreground()
        assertEquals("unknown", f.events("sigh_attempt_ended").single().field("outcome"))
    }

    @Test
    fun startFailureAndMemoCancellationDoNotCreateSave() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(true)
        m.startFailed("location_unavailable")
        m.beginAttempt(false)
        m.memoEditing()
        m.endAttempt()
        m.endAttempt()
        assertEquals(2, f.events("sigh_started").size)
        assertEquals(2, f.events("sigh_attempt_ended").size)
        assertTrue(f.events("save_started").isEmpty())
    }

    @Test
    fun memoCompletionStartsAtVisibleSheetAndDoesNotCollectContent() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.memoEditing()
        f.advance(250)
        m.memoShown()
        m.memoShown()
        f.advance(750)
        m.memoCompleted(memoPresent = true)

        assertEquals(1, f.events("memo_shown").size)
        val completed = f.events("memo_completed").single()
        assertEquals("true", completed.field("memo_present"))
        assertEquals("750", completed.field("memo_elapsed_ms"))
        assertEquals("awaiting_breath", completed.field("state"))
        assertNull(completed.field("memo"))
    }

    @Test
    fun memoSkipAndCompletionAreMutuallyExclusive() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.memoEditing()
        m.memoShown()
        f.advance(100)
        m.memoSkipped()
        m.memoCompleted(memoPresent = false)

        assertEquals("100", f.events("memo_skipped").single().field("memo_elapsed_ms"))
        assertTrue(f.events("memo_completed").isEmpty())
    }

    @Test
    fun everyMemoValidationFailureIsRecordedWithoutMemoContent() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.memoEditing()
        m.memoValidationFailed()
        m.memoValidationFailed()

        assertEquals(2, f.events("memo_validation_failed").size)
        assertTrue(f.events("memo_validation_failed").all { it.field("reason") == "invalid_memo" })
        assertTrue(f.events("memo_validation_failed").all { it.field("memo") == null })
    }

    @Test
    fun microphoneTimingAndFirstDetectionUseOneCapture() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.memoEditing()
        f.advance(100)
        m.memoCompleted(memoPresent = false)
        m.microphonePermissionResult(granted = true)
        val capture = m.beginCapture()!!
        f.advance(50)
        m.microphoneReady()
        f.advance(100)
        m.soundFirstDetected(strength = 0.8f, activeThreshold = 0.18f)
        m.soundFirstDetected(strength = 0.9f, activeThreshold = 0.12f)

        assertEquals("granted", f.events("permission_result").single().field("status"))
        assertEquals(capture.captureId, f.events("mic_start_requested").single().field("capture_id"))
        assertEquals("1", f.events("mic_start_requested").single().field("capture_index"))
        assertEquals("50", f.events("mic_ready").single().field("start_to_ready_ms"))
        assertEquals("50", f.events("mic_ready").single().field("memo_to_ready_ms"))
        val detected = f.events("sound_first_detected").single()
        assertEquals("250", detected.field("tap_to_detection_ms"))
        assertEquals("100", detected.field("mic_ready_to_detection_ms"))
        assertEquals("0.8", detected.field("strength"))
        assertEquals("0.18", detected.field("active_threshold"))
    }

    @Test
    fun microphoneFailureRecordsWhetherInputWasReady() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.beginCapture()
        m.microphoneFailed("start_failed")
        m.microphoneReady()
        m.microphoneFailed("microphone_unavailable")

        assertEquals(listOf("start", "read"), f.events("mic_failed").map { it.field("stage") })
    }

    @Test
    fun shortSilentGapIsMergedAndLongGapStartsANewSegment() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.beginCapture()
        m.microphoneReady()

        m.breathSample(active = true, sampleElapsedMs = 0, growth = 0f)
        m.breathSample(active = true, sampleElapsedMs = 100, growth = 0.1f)
        m.breathSample(active = false, sampleElapsedMs = 100, growth = 0.1f)
        m.breathSample(active = true, sampleElapsedMs = 50, growth = 0.2f)
        m.breathSample(active = false, sampleElapsedMs = 200, growth = 0.2f)
        m.breathSample(active = true, sampleElapsedMs = 50, growth = 0.3f)
        m.breathSample(active = true, sampleElapsedMs = 100, growth = 0.4f)
        m.endCapture(finalGrowth = 0.6f, stopReason = "release", interrupted = false)

        val segments = f.events("breath_segment_ended")
        assertEquals(2, segments.size)
        assertEquals("250", segments[0].field("span_ms"))
        assertEquals("100", segments[0].field("active_ms"))
        assertNull(segments[0].field("gap_before_ms"))
        assertEquals("250", segments[1].field("gap_before_ms"))
        assertEquals("100", segments[1].field("span_ms"))
        assertEquals("release", segments[1].field("end_reason"))

        val summary = f.events("breath_summary").single()
        assertEquals("2", summary.field("segment_count"))
        assertEquals("0.6", summary.field("final_growth"))
        assertEquals("true", summary.field("detected"))
        assertEquals("complete", summary.field("observation_quality"))
        assertEquals("release", summary.field("stop_reason"))
        assertEquals("true", f.events("mic_stopped").single().field("ready_observed"))
    }

    @Test
    fun longSampleGapMarksSummaryPartialWithoutInventingActiveTime() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.beginCapture()
        m.microphoneReady()

        m.breathSample(active = true, sampleElapsedMs = 0, growth = 0f)
        m.breathSample(active = true, sampleElapsedMs = 100, growth = 0.1f)
        m.breathSample(active = true, sampleElapsedMs = 600, growth = 0.2f)
        m.endCapture(finalGrowth = 0.3f, stopReason = "background", interrupted = true)
        m.endCapture(finalGrowth = 0.3f, stopReason = "background", interrupted = true)

        val segments = f.events("breath_segment_ended")
        assertEquals(2, segments.size)
        assertEquals("observation_gap", segments.first().field("end_reason"))
        assertEquals("100", segments.first().field("active_ms"))
        assertEquals("0", segments.last().field("active_ms"))
        assertEquals("partial", f.events("breath_summary").single().field("observation_quality"))
        assertEquals(1, f.events("mic_stopped").size)
        assertEquals(2, f.transport.flushes)
    }

    @Test
    fun attemptCancellationClosesCaptureBeforeClearingIt() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.beginCapture()
        m.microphoneReady()
        m.breathSample(active = true, sampleElapsedMs = 0, growth = 0.4f)

        m.endAttempt()
        m.endCapture(finalGrowth = 0.9f, stopReason = "user_cancel", interrupted = false)

        val summary = f.events("breath_summary").single()
        assertEquals("0.4", summary.field("final_growth"))
        assertEquals("user_cancel", summary.field("stop_reason"))
        assertEquals(1, f.events("mic_stopped").size)
    }

    @Test
    fun growthReleaseAndAnimationStayLinkedAfterMicrophoneStops() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        val capture = m.beginCapture()!!
        m.microphoneReady()
        f.advance(120)
        m.breathSample(active = true, sampleElapsedMs = 16, growth = 0.2f)
        m.releaseReady(growth = 0.8f, minimumReleaseProgress = 0.75f)
        m.controlTapped("ready", growth = 0.8f, inputActive = true)
        m.swipeAttempted(80f, 500f, 0.8f, success = true, reason = "released")
        m.endCapture(finalGrowth = 0.8f, stopReason = "release", interrupted = false)
        f.advance(300)
        m.releaseAnimationFinished()

        assertEquals(capture.captureId, f.events("sound_growth_started").single().field("capture_id"))
        assertEquals("1", f.events("sigh_control_tapped").single().field("tap_index"))
        assertEquals("success", f.events("sigh_swipe_attempted").single().field("outcome"))
        assertEquals("300", f.events("sigh_release_animation_finished").single().field("release_to_animation_end_ms"))
    }

    @Test
    fun saveApiUiAndRenderedStarKeepTheOriginalSaveIds() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        val save = m.beginSave()!!
        f.advance(250)
        m.apiRequestFinished("/api/v2/sighs", "POST", 250, success = true, statusCode = 201)
        m.saveResult(save, success = true, durationMs = 250)
        m.saveWaitFinished(save, 1750)
        f.advance(1750)
        m.saveUiResultShown("success")
        m.mapVisitStarted("foreground")
        f.advance(100)
        m.savedStarVisible()

        val api = f.events("api_request_finished").single()
        val ui = f.events("save_ui_result_shown").single()
        val star = f.events("sigh_saved_star_visible").single()
        assertEquals(save.saveAttemptId, api.field("save_attempt_id"))
        assertEquals(save.saveAttemptId, ui.field("save_attempt_id"))
        assertEquals(save.saveAttemptId, star.field("save_attempt_id"))
        assertEquals("2000", ui.field("save_feedback_elapsed_ms"))
        assertEquals("2100", star.field("save_to_star_visible_ms"))
        assertTrue(star.field("map_visit_id") != null)
    }

    @Test
    fun mapExposureAndSelectionsAreScopedToOneMapVisit() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.mapVisitStarted("foreground")
        m.mapStarsVisible(3)
        m.mapStarsVisible(5)
        m.starSelected("map")
        val firstSelection = f.events("star_selected").single().field("selection_id")
        m.starSelected("list")
        m.starDetailShown()
        m.mapVisitEnded("screen_hidden")

        assertEquals(1, f.events("map_stars_visible").size)
        assertEquals("3", f.events("map_stars_visible").single().field("visible_star_count"))
        assertEquals(firstSelection, f.events("star_detail_failed").single().field("selection_id"))
        assertEquals("superseded", f.events("star_detail_failed").single().field("reason"))
        assertEquals("list", f.events("star_detail_shown").single().field("entry_source"))
        assertEquals(
            f.events("map_visit_started").single().field("map_visit_id"),
            f.events("map_visit_ended").single().field("map_visit_id"),
        )
    }

    @Test
    fun firstSaveUsesResultDateAndSurvivesRestart() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        val save = m.beginSave()
        f.wall = Instant.parse("2026-09-17T14:59:59Z").toEpochMilliseconds()
        m.saveResult(save, true, 300)
        f.advance(2000)
        val second = f.create()
        second.foreground()
        second.beginAttempt(false)
        second.saveResult(second.beginSave(), true, 300)
        assertEquals("2026-09-17", f.events("first_sigh_saved").single().field("cohort_date"))
    }

    @Test
    fun legacyInstallsDoNotProduceFirstUseOrFirstSave() {
        val f = Fixture()
        val m = f.create(knownNew = false)
        m.foreground()
        m.beginAttempt(false)
        m.saveResult(m.beginSave(), true, 10)
        assertTrue(f.events("app_first_opened").isEmpty())
        assertTrue(f.events("first_sigh_saved").isEmpty())
        assertEquals("legacy_unknown", f.events("save_result").single().field("install_class"))
    }

    @Test
    fun persistedPendingEventsKeepIdsAndTimestampAfterRestart() {
        val f = Fixture()
        f.transport.accept = false
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.saveResult(m.beginSave(), true, 100)
        val pendingFirstSave = f.transport.offers.last { it.name == "app_first_opened" }
        // A new process drains old events before its own observations.
        f.advance(5000)
        f.transport.accept = true
        f.create().foreground()
        val delivered = f.events("app_first_opened").single()
        assertEquals(pendingFirstSave, delivered)
        assertEquals(1, f.events("first_sigh_saved").size)
    }

    @Test
    fun repeatedResultAfterRestartDoesNotGenerateANewEvent() {
        val f = Fixture()
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        val save = m.beginSave()
        m.saveResult(save, true, 100)
        f.create().saveResult(save, true, 100)
        assertEquals(1, f.events("save_result").size)
    }

    @Test
    fun persistenceFailureNeverTransmitsAnUnpersistedFirstEvent() {
        val f = Fixture()
        val m = f.create()
        f.store.fail = true
        m.foreground()
        assertFalse(m.recordingAvailable)
        assertTrue(f.transport.events.isEmpty())
        f.store.fail = false
        f.create().foreground()
        assertEquals(1, f.events("app_first_opened").size)
    }

    @Test
    fun corruptStorageIsNotOverwrittenOrDeclaredNew() {
        val f = Fixture()
        f.store.value = "broken"
        val m = f.create()
        m.foreground()
        assertFalse(m.recordingAvailable)
        assertEquals("broken", f.store.value)
        assertTrue(f.transport.events.isEmpty())
    }

    @Test
    fun disabledCollectionAndThrowingTransportDoNotThrow() {
        val f = Fixture()
        val disabled = f.create(enabled = false)
        disabled.foreground()
        assertFalse(disabled.beginAttempt(false))
        assertTrue(f.transport.events.isEmpty())
        f.transport.throws = true
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.report(IllegalStateException("test"))
        m.background()
        assertTrue(m.recordingAvailable)
    }

    @Test
    fun environmentMismatchDoesNotReuseIdentityForAnotherProject() {
        val f = Fixture()
        f.create().foreground()
        val wrongEnvironment = Monitoring(config("prod"), f.store, f.transport)
        assertFalse(wrongEnvironment.recordingAvailable)
    }

    @Test
    fun pendingQueueIsBoundedButPreservesFirstFacts() {
        val f = Fixture()
        f.transport.accept = false
        val m = f.create()
        m.foreground()
        m.beginAttempt(false)
        m.saveResult(m.beginSave(), true, 100)
        repeat(550) {
            m.beginAttempt(false)
            m.endAttempt()
        }
        assertTrue(m.droppedEventCount > 0)
        f.transport.accept = true
        m.tick()
        assertEquals(1000, f.transport.events.size)
        assertEquals(1, f.events("app_first_opened").size)
        assertEquals(1, f.events("first_sigh_saved").size)
    }

    @Test
    fun journalAcknowledgementFailureReplaysTheSameEventIds() {
        val f = Fixture()
        val m = f.create()
        f.transport.afterAccept = { f.store.fail = true }
        m.foreground()
        val original = f.events("app_first_opened").single()
        f.store.fail = false
        f.transport.afterAccept = {}
        f.create().foreground()
        val copies = f.events("app_first_opened")
        assertEquals(2, copies.size)
        assertEquals(original, copies.last())
        assertEquals(1, copies.distinctBy { it.field("event_id") }.size)
    }

    private fun MonitoringEvent.field(key: String): String? = properties[key]?.jsonPrimitive?.content

    private class Store : MonitoringStore {
        var value: String? = null
        var fail = false

        override fun read(): String? = value

        override fun write(value: String) {
            check(!fail)
            this.value = value
        }
    }

    private class Transport : MonitoringTransport {
        var accept = true
        var throws = false
        var flushes = 0
        var afterAccept: () -> Unit = {}
        val events = mutableListOf<MonitoringEvent>()
        val offers = mutableListOf<MonitoringEvent>()

        override fun track(event: MonitoringEvent): Boolean {
            check(!throws)
            offers += event
            if (accept) {
                events += event
                afterAccept()
            }
            return accept
        }

        override fun context(snapshot: MonitoringSnapshot?) {
            check(!throws)
        }

        override fun report(
            error: Throwable,
            snapshot: MonitoringSnapshot?,
        ) {
            check(!throws)
        }

        override fun flush() {
            check(!throws)
            flushes += 1
        }
    }

    private class Fixture {
        val store = Store()
        val transport = Transport()
        var wall = Instant.parse("2026-09-17T00:00:00Z").toEpochMilliseconds()
        var monotonic = 0L
        var nextId = 0

        fun create(
            knownNew: Boolean = true,
            enabled: Boolean = true,
        ) = Monitoring(
            config(enabled = enabled),
            store,
            transport,
            { wall },
            { monotonic },
            { "id-${++nextId}" },
            knownNew,
        )

        fun advance(ms: Long) {
            wall += ms
            monotonic += ms
        }

        fun events(name: String) = transport.events.filter { it.name == name }
    }

    companion object {
        private fun config(
            environment: String = "dev",
            enabled: Boolean = true,
        ) = MonitoringConfig(
            environment,
            "android",
            "1.0",
            "1",
            "test",
            enabled,
            "test-token",
            "https://example.com",
            "https://example.com/1",
        )
    }
}
