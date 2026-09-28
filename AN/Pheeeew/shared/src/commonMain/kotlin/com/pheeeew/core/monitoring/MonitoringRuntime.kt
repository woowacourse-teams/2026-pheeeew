package com.pheeeew.core.monitoring

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** One consumer owns state, disk writes and SDK handoff; callers only enqueue immutable commands. */
class MonitoringRuntime(
    private val config: MonitoringConfig,
    private val registry: EventRegistry,
    private val store: MonitoringStore,
    private val decoder: StateDecoder = StateDecoder { monitoringJson.decodeFromString<CollectionState>(it) },
    private val initializeTransport: suspend (String) -> MonitoringTransport,
    private val newInstallation: Boolean = false,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) : Monitoring {
    private val owner = newId()
    private val session = MutableStateFlow(newId())
    private var backgroundAt: TimeSource.Monotonic.ValueTimeMark? = null
    private var foregroundRequested = false
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val queuedData = MutableStateFlow(0)
    private val initial = CompletableDeferred<CollectionState?>()
    private val rejected = MutableStateFlow(0L)
    val rejectedEventCount = rejected.asStateFlow()
    private val droppedProperties = MutableStateFlow(0L)
    val droppedPropertyCount = droppedProperties.asStateFlow()
    private var state: CollectionState? = null
    private var transport: MonitoringTransport? = null
    private var sequence = 0L
    private var active = false
    private var healthy = false

    init {
        scope.launch {
            try {
                if (config.configured) {
                    val raw = store.read()
                    val loaded =
                        raw?.let(decoder::decode)
                            ?: CollectionState(newId(), config.environment, knownNew = newInstallation)
                    require(loaded.environment == config.environment && loaded.version == 3)
                    require(loaded.anonymousId.isNotBlank())
                    val validPending =
                        loaded.pending.mapNotNull { event ->
                            val fields =
                                event.properties
                                    .mapNotNull { (key, value) ->
                                        (value as? JsonPrimitive)?.let { key to it }
                                    }.toMap()
                            val safe = registry.sanitizeEnvelope(event.name, fields)
                            if (safe == null || safe["anonymous_id"] != JsonPrimitive(loaded.anonymousId) ||
                                safe["environment"] != JsonPrimitive(config.environment)
                            ) {
                                rejected.update { it + 1 }
                                null
                            } else {
                                event.copy(properties = safe)
                            }
                        }
                    val bounded = validPending.toMutableList()
                    while (bounded.size > config.queueCapacity) {
                        val ordinary = bounded.indexOfFirst { !preserved(it) }
                        bounded.removeAt(if (ordinary >= 0) ordinary else bounded.lastIndex)
                        rejected.update { it + 1 }
                    }
                    state = loaded.copy(pending = bounded)
                    // Persist migration and identity before starting an SDK.
                    store.write(monitoringJson.encodeToString(state!!))
                    transport = initializeTransport(loaded.anonymousId)
                    healthy = true
                }
            } catch (_: Exception) {
                rejected.update { it + 1 }
            } finally {
                initial.complete(state.takeIf { healthy })
            }
            for (command in commands) {
                try {
                    command()
                } catch (_: Exception) {
                    healthy = false
                    rejected.update { it + 1 }
                }
            }
        }
        scope.launch {
            while (true) {
                delay(30_000)
                enqueue(control = true) {
                    if (healthy) {
                        if (active) {
                            activeDay(context("unknown"))
                            state = state!!.copy(lastObserved = now())
                            persist()
                        }
                        drain()
                    }
                }
            }
        }
    }

    override fun context(
        screen: String,
        parent: EventContext?,
    ): EventContext =
        EventContext(
            owner,
            parent?.takeIf { it.owner == owner }?.sessionId ?: session.value,
            newId(),
            registry.screen(screen),
            parent?.takeIf { it.owner == owner }?.operationId,
        )

    override fun track(
        event: MonitoringEvent,
        context: EventContext,
    ) {
        if (!config.configured) return
        try {
            val definition = event.definition
            val fields = event.properties().mapValues { it.value.primitive() }
            val safe = registry.validate(definition, fields)
            if (safe == null || context.owner != owner) {
                rejected.update { it + 1 }
                return
            }
            droppedProperties.update { it + (fields.size - safe.size) }
            val timestamp = now()
            enqueue { if (healthy) record(definition, safe, context, timestamp) }
        } catch (_: Exception) {
            rejected.update { it + 1 }
        }
    }

    override fun reportError(
        error: Throwable,
        context: EventContext,
    ) {
        if (context.owner != owner || !config.configured) return
        enqueue {
            if (healthy) {
                runCatching {
                    transport?.report(
                        error,
                        mapOf(
                            "session_id" to context.sessionId,
                            "operation_id" to context.operationId,
                            "screen" to context.screen,
                        ),
                    )
                }
            }
        }
    }

    /** App lifecycle owner calls these on its main thread. Feature calls may come from any thread. */
    fun foreground() {
        if (foregroundRequested) return
        foregroundRequested = true
        val expired = backgroundAt?.elapsedNow()?.inWholeMilliseconds?.let { it >= config.visitTimeoutMs } == true
        if (expired) session.value = newId()
        val context = context("unknown")
        enqueue(control = true) {
            if (!healthy) return@enqueue
            val current = state!!
            if (current.visitId != context.sessionId) {
                current.visitId?.let { old ->
                    record(
                        LifecycleEvents.ended,
                        JsonObject(
                            mapOf("reason" to JsonPrimitive(if (expired) "timeout" else "process_interrupted")),
                        ),
                        context.copy(sessionId = old),
                        now(),
                    )
                }
                val first = current.knownNew && current.firstOpened == null
                state =
                    state!!.copy(
                        visitId = context.sessionId,
                        firstVisitId = if (first) context.sessionId else current.firstVisitId,
                        firstOpened = if (first) now() else current.firstOpened,
                    )
                // Facts and their pending events are committed together in record().
                if (first) record(LifecycleEvents.firstOpened, JsonObject(emptyMap()), context, now())
                record(
                    LifecycleEvents.started,
                    JsonObject(mapOf("first_visit" to JsonPrimitive(first))),
                    context,
                    now(),
                )
            }
            active = true
            activeDay(context)
            state = state!!.copy(lastObserved = now())
            persist()
            drain()
            runCatching { transport?.flush() }
        }
    }

    fun background() {
        if (!foregroundRequested) return
        foregroundRequested = false
        backgroundAt = TimeSource.Monotonic.markNow()
        val context = context("unknown")
        enqueue(control = true) {
            if (!healthy) return@enqueue
            active = false
            state = state!!.copy(lastObserved = now())
            record(LifecycleEvents.backgrounded, JsonObject(emptyMap()), context, now())
            runCatching { transport?.flush() }
        }
    }

    fun flush() {
        enqueue(control = true) {
            if (healthy) {
                drain()
                runCatching { transport?.flush() }
            }
        }
    }

    suspend fun awaitInitialState(): CollectionState? = initial.await()

    internal suspend fun currentState(): CollectionState? {
        if (initial.await() == null) return null
        val result = CompletableDeferred<CollectionState?>()
        commands.send { result.complete(state.takeIf { healthy }) }
        return result.await()
    }

    /** Barrier for composition/verification, not a remote-delivery guarantee. */
    suspend fun awaitIdle() {
        val done = CompletableDeferred<Unit>()
        commands.send { done.complete(Unit) }
        done.await()
    }

    suspend fun close() {
        flush()
        awaitIdle()
        commands.close()
        scope.cancel()
    }

    internal fun saveExtension(
        key: String,
        value: JsonObject,
    ): Boolean =
        enqueue {
            if (healthy) {
                state = state!!.copy(extensions = state!!.extensions + (key to value))
                persist()
            }
        }

    /** Only the app's old-format bridge uses this; feature API cannot supply reserved fields. */
    internal fun replayCompatible(event: EventEnvelope): Boolean {
        val fields = event.properties.mapValues { (_, value) -> value as? JsonPrimitive ?: return false }
        val safe = registry.sanitizeEnvelope(event.name, fields) ?: return false
        return enqueue {
            if (healthy && safe["anonymous_id"] == JsonPrimitive(state!!.anonymousId) &&
                safe["environment"] == JsonPrimitive(config.environment)
            ) {
                append(event.copy(properties = safe))
            }
        }
    }

    // Low-frequency lifecycle/barrier commands cannot be displaced by a burst of events.
    // Data commands (including compatibility snapshots) have a separate bounded admission budget.
    private fun enqueue(
        control: Boolean = false,
        command: suspend () -> Unit,
    ): Boolean {
        if (!config.configured) return false
        if (!control) {
            while (true) {
                val size = queuedData.value
                if (size >= config.queueCapacity) {
                    rejected.update { it + 1 }
                    return false
                }
                if (queuedData.compareAndSet(size, size + 1)) break
            }
        }
        val accepted =
            commands
                .trySend {
                    try {
                        command()
                    } finally {
                        if (!control) queuedData.update { it - 1 }
                    }
                }.isSuccess
        if (!accepted) {
            if (!control) queuedData.update { it - 1 }
            rejected.update { it + 1 }
        }
        return accepted
    }

    private fun activeDay(context: EventContext) {
        val date = config.activeDay(now())
        if (date in state!!.activeDays) return
        state = state!!.copy(activeDays = (state!!.activeDays + date).takeLast(4096))
        record(LifecycleEvents.activeDay, JsonObject(mapOf("activity_date" to JsonPrimitive(date))), context, now())
    }

    private fun record(
        definition: EventDefinition,
        fields: JsonObject,
        context: EventContext,
        time: Long,
    ) {
        val common =
            mapOf(
                "event_id" to JsonPrimitive(newId()),
                "event_schema_version" to JsonPrimitive(definition.version),
                "measurement_config_version" to JsonPrimitive("v3"),
                "anonymous_id" to JsonPrimitive(state!!.anonymousId),
                "occurred_at" to JsonPrimitive(Instant.fromEpochMilliseconds(time).toString()),
                "event_sequence" to JsonPrimitive(++sequence),
                "process_id" to JsonPrimitive(owner),
                "session_id" to JsonPrimitive(context.sessionId),
                "operation_id" to JsonPrimitive(context.operationId),
                "screen" to JsonPrimitive(context.screen),
                "environment" to JsonPrimitive(config.environment),
                "platform" to JsonPrimitive(config.platform),
                "app_version" to JsonPrimitive(config.appVersion),
                "build_number" to JsonPrimitive(config.buildNumber),
                "os_version" to JsonPrimitive(config.osVersion),
                "device_class" to JsonPrimitive(config.deviceClass),
            ) + config.collection.eventProperties() +
                context.parentOperationId?.let { mapOf("parent_operation_id" to JsonPrimitive(it)) }.orEmpty()
        append(EventEnvelope(definition.name, time, JsonObject(fields + common)))
    }

    private fun append(event: EventEnvelope) {
        val pending = state!!.pending.toMutableList()
        if (pending.any { it.properties["event_id"] == event.properties["event_id"] }) return
        if (pending.size >= config.queueCapacity) {
            val discard = pending.indexOfFirst { !preserved(it) }
            if (discard < 0) {
                rejected.update { it + 1 }
                return
            }
            pending.removeAt(discard)
            rejected.update { it + 1 }
        }
        pending.add(event)
        state = state!!.copy(pending = pending)
        persist()
        drain()
    }

    private fun preserved(event: EventEnvelope): Boolean =
        registry
            .definition(
                event.name,
                (event.properties["event_schema_version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1,
            )?.preserveOnOverflow == true

    private fun persist() {
        store.write(monitoringJson.encodeToString(state!!))
    }

    private fun drain() {
        val pending = state!!.pending
        val accepted = pending.takeWhile { runCatching { transport?.track(it) == true }.getOrDefault(false) }
        if (accepted.isNotEmpty()) {
            state = state!!.copy(pending = pending.drop(accepted.size))
            persist()
        }
    }
}

object LifecycleEvents {
    val firstOpened = EventDefinition("app_first_opened", 2, preserveOnOverflow = true)
    val started =
        EventDefinition(
            "app_visit_started",
            2,
            mapOf("first_visit" to PropertyRule(ValueType.BOOLEAN, required = true)),
        )
    val ended =
        EventDefinition(
            "app_visit_ended",
            2,
            mapOf(
                "reason" to
                    PropertyRule(
                        ValueType.TEXT,
                        required = true,
                        allowed = setOf("timeout", "process_interrupted"),
                    ),
            ),
        )
    val backgrounded = EventDefinition("app_backgrounded", 2)
    val activeDay =
        EventDefinition(
            "app_active_day",
            2,
            mapOf("activity_date" to PropertyRule(ValueType.TEXT, required = true)),
        )
    val definitions = listOf(firstOpened, started, ended, backgrounded, activeDay)
}
