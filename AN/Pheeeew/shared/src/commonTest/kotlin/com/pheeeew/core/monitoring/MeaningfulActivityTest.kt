package com.pheeeew.core.monitoring

import com.pheeeew.core.di.appMonitoringRegistry
import com.pheeeew.core.di.decodeAppMonitoringState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MeaningfulActivityTest {
    private val beforeMidnight = Instant.parse("2026-10-08T14:59:00Z").toEpochMilliseconds()
    private val afterMidnight = beforeMidnight + 120_000

    @Test
    fun `thousand presses and three action types produce only three records across restart`() =
        runTest {
            val store = Store()
            val transport = Transport()
            val runtime = runtime(store, transport) { beforeMidnight }
            runCurrent()
            repeat(1000) { runtime.recordSuccessfulActivity(ActivityType.PERSONAL_PRESS, beforeMidnight) }
            runCurrent()
            runtime.recordSuccessfulActivity(ActivityType.EMOTION_RECORD, beforeMidnight)
            runtime.recordSuccessfulActivity(ActivityType.GROUP_PRESS, beforeMidnight)
            runCurrent()
            assertEquals(3, transport.events.size)
            assertEquals(
                setOf("personal_press", "emotion_record", "group_press"),
                transport.events
                    .map {
                        it.properties["activity_type"]!!.toString().trim('"')
                    }.toSet(),
            )
            val identity = assertNotNull(runtime.awaitInitialState()).anonymousId
            runtime.close()
            val restored = runtime(store, transport) { beforeMidnight }
            runCurrent()
            ActivityType.entries.forEach { restored.recordSuccessfulActivity(it, beforeMidnight) }
            runCurrent()
            assertEquals(3, transport.events.size)
            assertEquals(identity, assertNotNull(restored.awaitInitialState()).anonymousId)
            restored.close()
        }

    @Test
    fun `delayed success keeps original KST day and tomorrow is a new key`() =
        runTest {
            val transport = Transport()
            val runtime = runtime(Store(), transport) { afterMidnight }
            runCurrent()
            runtime.recordSuccessfulActivity(ActivityType.PERSONAL_PRESS, beforeMidnight)
            runtime.recordSuccessfulActivity(ActivityType.PERSONAL_PRESS, afterMidnight)
            runCurrent()
            assertEquals(
                listOf("2026-10-08", "2026-10-09"),
                transport.events.map {
                    (it.properties["activity_date"] as JsonPrimitive).content
                },
            )
            assertEquals(listOf(beforeMidnight, afterMidnight), transport.events.map { it.timestamp })
            runtime.close()
        }

    @Test
    fun `restored outbox retains event identity and blocks legacy events without disabling errors`() =
        runTest {
            val store = Store()
            val offline = Transport(accept = false)
            val runtime = runtime(store, offline) { beforeMidnight }
            runCurrent()
            runtime.recordSuccessfulActivity(ActivityType.EMOTION_RECORD, beforeMidnight)
            runCurrent()
            val pending = monitoringJson.decodeFromString<CollectionState>(store.raw!!).pending.single()
            runtime.close()
            val loaded = monitoringJson.decodeFromString<CollectionState>(store.raw!!)
            store.raw =
                monitoringJson.encodeToString(
                    loaded.copy(
                        pending =
                            loaded.pending + pending.copy(name = "http_attempt_finished"),
                    ),
                )
            val online = Transport()
            val restored = runtime(store, online) { beforeMidnight }
            runCurrent()
            restored.foreground()
            restored.track(DefinedEvent(LifecycleEvents.firstOpened), restored.context("map"))
            restored.reportError(IllegalStateException("failure"), restored.context("map"))
            runCurrent()
            restored.background()
            runCurrent()
            assertEquals(listOf(pending), online.events)
            assertEquals(1, online.errors)
            assertEquals(1, monitoringJson.decodeFromString<CollectionState>(store.raw!!).meaningfulDays.size)
            restored.close()
        }

    @Test
    fun `future and expired inputs do not consume daily keys`() =
        runTest {
            val transport = Transport()
            val store = Store()
            val runtime = runtime(store, transport) { beforeMidnight }
            runCurrent()
            runtime.recordSuccessfulActivity(ActivityType.PERSONAL_PRESS, beforeMidnight + 1)
            runtime.recordSuccessfulActivity(ActivityType.PERSONAL_PRESS, beforeMidnight - ACTIVITY_MAX_AGE - 1)
            runCurrent()
            assertTrue(transport.events.isEmpty())
            assertTrue(monitoringJson.decodeFromString<CollectionState>(store.raw!!).meaningfulDays.isEmpty())
            runtime.close()
        }

    @Test
    fun `write failure never hands off an event or persists only its receipt`() =
        runTest {
            val store = Store()
            val transport = Transport()
            val runtime = runtime(store, transport) { beforeMidnight }
            runCurrent()
            store.fail = true
            runtime.recordSuccessfulActivity(ActivityType.GROUP_PRESS, beforeMidnight)
            runCurrent()
            assertTrue(transport.events.isEmpty())
            assertTrue(monitoringJson.decodeFromString<CollectionState>(store.raw!!).meaningfulDays.isEmpty())
            runtime.close()
        }

    @Test
    fun `event contains only report properties and production is not assumed external`() =
        runTest {
            val transport = Transport()
            val runtime = runtime(Store(), transport) { beforeMidnight }
            runCurrent()
            runtime.recordSuccessfulActivity(ActivityType.EMOTION_RECORD, beforeMidnight)
            runCurrent()
            val event = transport.events.single()
            assertEquals("unknown", (event.properties["audience"] as JsonPrimitive).content)
            assertEquals(
                setOf(
                    "anonymous_id",
                    "event_id",
                    "event_schema_version",
                    "activity_date",
                    "activity_type",
                    "audience",
                    "environment",
                    "platform",
                    "app_version",
                    "measurement_version",
                ),
                event.properties.keys,
            )
            assertFalse(event.properties.containsKey("screen"))
            runtime.close()
        }

    private fun TestScope.runtime(
        store: Store,
        transport: Transport,
        time: () -> Long,
    ): MonitoringRuntime =
        MonitoringRuntime(
            MonitoringConfig(
                "prod",
                "android",
                "2.2",
                "8",
                "test",
                true,
                "test-token",
                "https://example.com",
                "https://example.com",
            ),
            appMonitoringRegistry(),
            store,
            StateDecoder(::decodeAppMonitoringState),
            initializeTransport = { transport },
            scope =
                kotlinx.coroutines.CoroutineScope(
                    backgroundScope.coroutineContext +
                        kotlinx.coroutines.SupervisorJob(backgroundScope.coroutineContext[kotlinx.coroutines.Job]),
                ),
            now = time,
        )

    private class Store : MonitoringStore {
        var raw: String? = null
        var fail = false

        override fun read() = raw

        override fun write(value: String) {
            check(!fail)
            raw = value
        }
    }

    private class Transport(
        val accept: Boolean = true,
    ) : MonitoringTransport {
        val events = mutableListOf<EventEnvelope>()
        var errors = 0

        override fun track(event: EventEnvelope): Boolean {
            if (accept) events += event
            return accept
        }

        override fun report(
            error: Throwable,
            context: Map<String, String>,
        ) {
            errors++
        }

        override fun flush() = Unit
    }
}
