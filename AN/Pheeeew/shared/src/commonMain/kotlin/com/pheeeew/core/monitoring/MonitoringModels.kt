package com.pheeeew.core.monitoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// No generated toString: configuration contains connection credentials.
class MonitoringConfig(
    val environment: String,
    val platform: String,
    val appVersion: String,
    val buildNumber: String,
    val osVersion: String,
    val enabled: Boolean,
    val posthogToken: String,
    val posthogHost: String,
    val sentryDsn: String,
    val deviceClass: String = "unknown",
) {
    val configured: Boolean get() =
        enabled && environment in setOf("dev", "prod") && posthogToken.isNotBlank() &&
            posthogHost.startsWith("https://") &&
            sentryDsn.startsWith("https://")
}

@Serializable
data class MonitoringSnapshot(
    val sessionId: String,
    val sighAttemptId: String? = null,
    val saveAttemptId: String? = null,
    val captureId: String? = null,
    val captureIndex: Int? = null,
    val screen: String = "unknown",
    val state: String = "unknown",
) {
    fun properties(): Map<String, String> =
        buildMap {
            put("session_id", sessionId)
            sighAttemptId?.let { put("sigh_attempt_id", it) }
            saveAttemptId?.let { put("save_attempt_id", it) }
            captureId?.let { put("capture_id", it) }
        }
}

@Serializable
internal data class MonitoringState(
    val anonymousId: String,
    val environment: String,
    val version: Int = 1,
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

@Serializable
data class MonitoringEvent(
    val name: String,
    val timestamp: Long,
    val properties: JsonObject,
)

interface MonitoringStore {
    fun read(): String?

    fun write(value: String)
}

interface MonitoringTransport {
    /** True means accepted by the SDK, not acknowledged by the remote server. */
    fun track(event: MonitoringEvent): Boolean

    fun context(snapshot: MonitoringSnapshot?)

    fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    )
}
