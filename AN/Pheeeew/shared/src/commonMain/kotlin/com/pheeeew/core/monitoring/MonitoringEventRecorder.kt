package com.pheeeew.core.monitoring

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// 이벤트 생성·순서·저장·재전송
internal class MonitoringEventRecorder(
    private val config: MonitoringConfig,
    private val stateStore: MonitoringStateStore,
    private val transport: MonitoringTransport,
    private val processId: String,
    private val id: () -> String,
    private val now: () -> Long,
    private val anonymousId: () -> String,
) {
    fun record(
        definition: MonitoringEventDefinition,
        origin: MonitoringSnapshot,
        fields: Map<String, Any>,
        logicalKey: String?,
    ) {
        val current = stateStore.state
        if (logicalKey != null && logicalKey in current.logicalKeys) return
        val time = now()
        val properties =
            MonitoringValueSanitizer.fields(fields) + origin.properties() +
                mapOf(
                    "event_id" to id(),
                    "event_schema_version" to 1,
                    "measurement_config_version" to "v1",
                    "anonymous_id" to anonymousId(),
                    "occurred_at" to MonitoringTime.iso(time),
                    "process_id" to processId,
                    "event_sequence" to nextSequence(),
                    "screen" to origin.screen,
                    "state" to origin.state,
                    "environment" to config.environment,
                    "platform" to config.platform,
                    "app_version" to config.appVersion,
                    "build_number" to config.buildNumber,
                    "os_version" to config.osVersion,
                    "device_class" to config.deviceClass,
                    "install_class" to
                        if (!current.knownNew) {
                            "legacy_unknown"
                        } else if (origin.sessionId == current.firstVisitId) {
                            "new"
                        } else {
                            "returning"
                        },
                )
        val missing = definition.requiredProperties - properties.keys
        if (missing.isNotEmpty()) {
            stateStore.replace(current.copy(droppedEvents = current.droppedEvents + 1))
            return
        }
        val event = MonitoringEvent(definition.value, time, JsonObject(properties.mapValues { (_, value) -> primitive(value) }))
        // Keep state changes and the pending event in the same durable record before handoff.
        val pending = (current.pending + event).toMutableList()
        if (pending.size > MAX_PENDING) {
            // Preserve installation-first facts while dropping the oldest ordinary event.
            val discard =
                pending.indexOfFirst {
                    it.name != MonitoringEventNames.APP_FIRST_OPENED.value &&
                        it.name != MonitoringEventNames.FIRST_SIGH_SAVED.value
                }
            pending.removeAt(discard)
        }
        stateStore.replace(
            current.copy(
                pending = pending,
                droppedEvents = current.droppedEvents + if (current.pending.size >= MAX_PENDING) 1 else 0,
                logicalKeys =
                    if (logicalKey == null) {
                        current.logicalKeys
                    } else {
                        (current.logicalKeys + logicalKey).takeLast(MAX_LOGICAL_KEYS)
                    },
            ),
        )
    }

    fun persist(): Boolean = stateStore.persist()

    /** Returns false when pending events could not be persisted after delivery. */
    fun drain(): Boolean {
        val current = stateStore.state
        if (current.pending.isEmpty()) return true
        val accepted = current.pending.takeWhile { runCatching { transport.track(it) }.getOrDefault(false) }
        if (accepted.isEmpty()) return true
        stateStore.replace(current.copy(pending = current.pending.drop(accepted.size)))
        // Failure to acknowledge durably can replay the same IDs after restart, never new IDs.
        return persist()
    }

    private var sequence = 0L

    private fun nextSequence(): Long {
        sequence += 1
        return sequence
    }

    private companion object {
        const val MAX_PENDING = 1000
        const val MAX_LOGICAL_KEYS = 4096
    }
}

private fun primitive(value: Any): JsonPrimitive =
    when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> error("Unsupported monitoring property")
    }
