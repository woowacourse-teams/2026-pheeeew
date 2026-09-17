package com.pheeeew.core.monitoring

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
