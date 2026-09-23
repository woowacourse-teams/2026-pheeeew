package com.pheeeew.legacy.core.monitoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Event, Snapshot, Store, Transport
@Serializable
internal data class MonitoringSnapshot(
    val sessionId: String,
    val sighAttemptId: String? = null,
    val saveAttemptId: String? = null,
    val captureId: String? = null,
    val captureIndex: Int? = null,
    val mapVisitId: String? = null,
    val selectionId: String? = null,
    val screen: String = "unknown",
    val state: String = "unknown",
) {
    fun properties(): Map<String, String> =
        buildMap {
            put("session_id", sessionId)
            sighAttemptId?.let { put("sigh_attempt_id", it) }
            saveAttemptId?.let { put("save_attempt_id", it) }
            captureId?.let { put("capture_id", it) }
            captureIndex?.let { put("capture_index", it.toString()) }
            mapVisitId?.let { put("map_visit_id", it) }
            selectionId?.let { put("selection_id", it) }
        }
}

/**
 * Opaque operation handle exposed to app code. The snapshot stays inside the monitoring module;
 * the owner token prevents a handle from being reused by another Monitoring instance.
 */
class MonitoringHandle internal constructor(
    internal val ownerToken: Any,
    internal val snapshot: MonitoringSnapshot,
) {
    internal val sessionId: String get() = snapshot.sessionId
    internal val sighAttemptId: String? get() = snapshot.sighAttemptId
    internal val saveAttemptId: String? get() = snapshot.saveAttemptId
    internal val captureId: String? get() = snapshot.captureId
    internal val captureIndex: Int? get() = snapshot.captureIndex
    internal val screen: String get() = snapshot.screen
    internal val state: String get() = snapshot.state

    override fun equals(other: Any?): Boolean =
        other is MonitoringHandle && ownerToken === other.ownerToken && snapshot == other.snapshot

    override fun hashCode(): Int = 31 * ownerToken.hashCode() + snapshot.hashCode()
}

@Serializable
internal data class MonitoringState(
    val anonymousId: String,
    val environment: String,
    val version: Int = MonitoringStateMigrator.CURRENT_VERSION,
    val visitId: String? = null,
    val firstVisitId: String? = null,
    val lastObserved: Long = 0,
    val lastScreen: String = "unknown",
    val lastStage: String = "unknown",
    val activeDays: List<String> = emptyList(),
    val attempt: MonitoringSnapshot? = null,
    val savePending: Boolean = false,
    val knownNew: Boolean = false,
    val firstOpened: Long? = null,
    val firstSaved: Long? = null,
    val pending: List<MonitoringEvent> = emptyList(),
    val logicalKeys: List<String> = emptyList(),
    val droppedEvents: Long = 0,
)

/** Converts durable state between schema versions before it is used by the monitoring runtime. */
internal object MonitoringStateMigrator {
    const val CURRENT_VERSION = 2

    fun migrate(state: MonitoringState): MonitoringState =
        when (state.version) {
            1 -> state.copy(version = CURRENT_VERSION)
            CURRENT_VERSION -> state
            else -> error("Unsupported monitoring state version: ${state.version}")
        }
}

@Serializable
internal data class MonitoringEvent(
    val name: String,
    val timestamp: Long,
    val properties: JsonObject,
)

internal interface MonitoringStore {
    fun read(): String?

    fun write(value: String)
}

internal typealias MonitoringTransport = com.pheeeew.legacy.core.monitoring.transport.MonitoringTransport
