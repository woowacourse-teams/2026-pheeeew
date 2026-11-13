package com.pheeeew.core.monitoring

import com.pheeeew.core.di.appMonitoringRegistry
import com.pheeeew.core.di.decodeAppMonitoringState
import com.pheeeew.feature.monitoring.product.ProductEvents
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
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
    fun `restored queue retains collection claim clicks and filters retired events`() {
        val state =
            CollectionState(
                anonymousId = "install",
                environment = "dev",
                pending =
                    listOf(
                        EventEnvelope(MeaningfulActivity.NAME, 1L, JsonObject(emptyMap())),
                        EventEnvelope(
                            ProductEvents.COLLECTION_CLAIM_CLICKED,
                            2L,
                            JsonObject(emptyMap()),
                        ),
                        EventEnvelope("settings_action_selected", 3L, JsonObject(emptyMap())),
                    ),
            )

        val restored = decodeAppMonitoringState(monitoringJson.encodeToString(state))

        assertEquals(
            listOf(MeaningfulActivity.NAME, ProductEvents.COLLECTION_CLAIM_CLICKED),
            restored.pending.map(EventEnvelope::name),
        )
    }

    @Test
    fun `collection claim click is recorded with the collection screen`() =
        runTest {
            val transport = Transport()
            val runtime = runtime(Store(), transport) { beforeMidnight }
            runCurrent()

            ProductMonitoring(runtime, "collection").emit(ProductEvents.COLLECTION_CLAIM_CLICKED)
            runCurrent()

            val event = transport.events.single()
            assertEquals(ProductEvents.COLLECTION_CLAIM_CLICKED, event.name)
            assertEquals("collection", (event.properties["screen"] as JsonPrimitive).content)
            runtime.close()
        }

    @Test
    fun `thousand presses and two action types produce only two records across restart`() =
        runTest {
            val store = Store()
            val transport = Transport()
            val runtime = runtime(store, transport) { beforeMidnight }
            runCurrent()
            repeat(1000) { runtime.recordPressInput(beforeMidnight) }
            runCurrent()
            runtime.recordSuccessfulActivity(ActivityType.EMOTION_RECORD, beforeMidnight)
            runCurrent()
            assertEquals(2, transport.events.size)
            assertEquals(
                setOf("personal_press", "emotion_record"),
                transport.events
                    .map {
                        it.properties["activity_type"]!!.toString().trim('"')
                    }.toSet(),
            )
            val identity = assertNotNull(runtime.awaitInitialState()).anonymousId
            runtime.close()
            val restored = runtime(store, transport) { beforeMidnight }
            runCurrent()
            restored.recordSuccessfulActivity(ActivityType.EMOTION_RECORD, beforeMidnight)
            repeat(100) { restored.recordPressInput(beforeMidnight) }
            runCurrent()
            assertEquals(2, transport.events.size)
            assertEquals(identity, assertNotNull(restored.awaitInitialState()).anonymousId)
            restored.close()
        }

    @Test
    fun `delayed success keeps original KST day and tomorrow is a new key`() =
        runTest {
            val transport = Transport()
            val runtime = runtime(Store(), transport) { afterMidnight }
            runCurrent()
            repeat(100) { runtime.recordPressInput(beforeMidnight) }
            repeat(100) { runtime.recordPressInput(afterMidnight) }
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
            assertEquals(pending, online.events.first())
            assertEquals("app_open_day", online.events.last().name)
            assertEquals(1, online.errors)
            assertEquals(2, monitoringJson.decodeFromString<CollectionState>(store.raw!!).meaningfulDays.size)
            restored.close()
        }

    @Test
    fun `future and expired inputs do not consume daily keys`() =
        runTest {
            val transport = Transport()
            val store = Store()
            val runtime = runtime(store, transport) { beforeMidnight }
            runCurrent()
            runtime.recordPressInput(beforeMidnight + 1)
            runtime.recordPressInput(beforeMidnight - ACTIVITY_MAX_AGE - 1)
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
            runtime.recordSuccessfulActivity(ActivityType.EMOTION_RECORD, beforeMidnight)
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

    @Test
    fun `ninety nine taps survive restart and hundredth emits once independent of delivery`() =
        runTest {
            val store = Store()
            val transport = Transport()
            val runtime = runtime(store, transport) { beforeMidnight }
            runCurrent()
            repeat(99) { runtime.recordPressInput(beforeMidnight) }
            runCurrent()
            assertTrue(transport.events.isEmpty())
            runtime.close()
            val restored = runtime(store, transport) { beforeMidnight }
            runCurrent()
            restored.recordPressInput(beforeMidnight)
            repeat(10) { restored.recordSuccessfulActivity(ActivityType.PERSONAL_PRESS, beforeMidnight) }
            runCurrent()
            assertEquals(listOf("press_100_day"), transport.events.map { it.name })
            repeat(1000) { restored.recordPressInput(beforeMidnight) }
            runCurrent()
            assertEquals(1, transport.events.size)
            restored.close()
        }

    @Test
    fun `opening foreground once per KST day stays deduplicated across restart`() =
        runTest {
            val store = Store()
            val transport = Transport()
            var time = beforeMidnight
            val runtime = runtime(store, transport) { time }
            runCurrent()
            repeat(3) {
                runtime.foreground()
                runtime.background()
            }
            runCurrent()
            assertEquals(listOf("app_open_day"), transport.events.map { it.name })
            time = afterMidnight
            runtime.foreground()
            runCurrent()
            assertEquals(2, transport.events.size)
            runtime.close()
            val restored = runtime(store, transport) { time }
            runCurrent()
            restored.foreground()
            runCurrent()
            assertEquals(2, transport.events.size)
            restored.close()
        }

    @Test
    fun `card impression and save share clicks retain a common visit identifier after restore`() =
        runTest {
            val store = Store()
            val offline = Transport(accept = false)
            val runtime = runtime(store, offline) { beforeMidnight }
            runCurrent()
            val product = ProductMonitoring(runtime, "collection")
            val names =
                listOf("collection_card_viewed", "collection_card_save_clicked", "collection_card_share_clicked")
            names.forEach { product.emit(it, mapOf("view_id" to EventValue.Text("card-visit"))) }
            runCurrent()
            runtime.close()
            val online = Transport()
            val restored = runtime(store, online) { beforeMidnight }
            runCurrent()
            restored.flush()
            runCurrent()
            assertEquals(names, online.events.map { it.name })
            assertTrue(online.events.all { it.properties["view_id"] == JsonPrimitive("card-visit") })
            restored.close()
        }

    @Test
    fun `full offline queue retains threshold date and emits after midnight recovery`() =
        runTest {
            val store = Store()
            val runtime = runtime(store, Transport(accept = false), capacity = 2) { beforeMidnight }
            runCurrent()
            repeat(2) { ProductMonitoring(runtime, "collection").emit(ProductEvents.COLLECTION_CLAIM_CLICKED) }
            runCurrent()
            repeat(100) { runtime.recordPressInput(beforeMidnight) }
            runCurrent()
            val stored = monitoringJson.decodeFromString<CollectionState>(store.raw!!)
            assertEquals(2, stored.pending.size)
            assertTrue(stored.meaningfulDays.isEmpty())
            assertEquals(beforeMidnight, stored.pressThresholds["2026-10-08"])
            runtime.close()
            val online = Transport()
            val restored = runtime(store, online, capacity = 2) { afterMidnight }
            runCurrent()
            restored.foreground()
            runCurrent()
            advanceTimeBy(30_001)
            runCurrent()
            val press = online.events.single { it.name == "press_100_day" }
            assertEquals(beforeMidnight, press.timestamp)
            assertEquals(JsonPrimitive("2026-10-08"), press.properties["activity_date"])
            restored.close()
        }

    @Test
    fun `card view save and share each collect once per KST day across cards and process restart`() =
        runTest {
            val store = Store()
            val transport = Transport()
            var time = beforeMidnight
            val runtime = runtime(store, transport) { time }
            runCurrent()
            val names =
                listOf(
                    "collection_card_viewed",
                    "collection_card_save_clicked",
                    "collection_card_share_clicked",
                )
            repeat(10) { visit ->
                names.forEach {
                    ProductMonitoring(runtime, "collection").emit(
                        it,
                        mapOf("view_id" to EventValue.Text("visit-$visit")),
                    )
                }
            }
            runCurrent()
            assertEquals(names, transport.events.map { it.name })
            runtime.close()
            val restored = runtime(store, transport) { time }
            runCurrent()
            names.forEach {
                ProductMonitoring(restored, "collection").emit(
                    it,
                    mapOf("view_id" to EventValue.Text("reopened")),
                )
            }
            runCurrent()
            assertEquals(3, transport.events.size)
            time = afterMidnight
            names.forEach {
                ProductMonitoring(restored, "collection").emit(
                    it,
                    mapOf("view_id" to EventValue.Text("next-day")),
                )
            }
            runCurrent()
            assertEquals(names + names, transport.events.map { it.name })
            assertEquals(
                List(3) { "2026-10-08" } + List(3) { "2026-10-09" },
                transport.events.map { (it.properties.getValue("activity_date") as JsonPrimitive).content },
            )
            restored.close()
        }

    @Test
    fun `failed daily click persistence does not consume a daily receipt`() =
        runTest {
            val store = Store()
            val transport = Transport()
            val runtime = runtime(store, transport) { beforeMidnight }
            runCurrent()
            store.fail = true
            ProductMonitoring(runtime, "collection").emit(
                "collection_card_save_clicked",
                mapOf("view_id" to EventValue.Text("visit")),
            )
            runCurrent()
            assertTrue(transport.events.isEmpty())
            assertTrue(monitoringJson.decodeFromString<CollectionState>(store.raw!!).dailyProductEvents.isEmpty())
            runtime.close()
        }

    private fun TestScope.runtime(
        store: Store,
        transport: Transport,
        capacity: Int = 1000,
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
                queueCapacity = capacity,
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
