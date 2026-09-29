package com.pheeeew.core.monitoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface MonitoringStore {
    fun read(): String?

    fun write(value: String)
}

@Serializable
data class EventEnvelope(
    val name: String,
    val timestamp: Long,
    val properties: JsonObject,
)

@Serializable
data class CollectionState(
    val anonymousId: String,
    val environment: String,
    val version: Int = 3,
    val visitId: String? = null,
    val firstVisitId: String? = null,
    val firstOpened: Long? = null,
    val knownNew: Boolean = false,
    val lastObserved: Long = 0,
    val activeDays: List<String> = emptyList(),
    val pending: List<EventEnvelope> = emptyList(),
    val extensions: Map<String, JsonObject> = emptyMap(),
)

/** App supplies old-format conversion. The engine has no feature-specific migration dependency. */
fun interface StateDecoder {
    fun decode(raw: String): CollectionState
}

internal val monitoringJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

interface MonitoringTransport {
    /** SDK acceptance only, not a delivery receipt. */
    fun track(event: EventEnvelope): Boolean

    fun report(
        error: Throwable,
        context: Map<String, String>,
    )

    fun flush()
}
