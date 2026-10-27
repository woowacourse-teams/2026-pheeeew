package com.pheeeew.data.press.race

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal enum class PressRaceView { PRESS, ALL }

@Serializable
internal data class PressRaceReady(
    val serverGeneration: String,
    val connectionGeneration: String,
    val date: String,
    val counts: Map<String, Long>,
    val results: List<PressRaceInputResult>,
)

@Serializable
internal data class PressRaceInputResult(
    val requestId: String,
    val status: String,
    val appliedDate: String? = null,
    val appliedCounts: Map<String, Long>? = null,
    val code: String? = null,
)

@Serializable
internal data class PressRaceParticipant(
    val publicId: String?,
    val nickname: String?,
    val score: Long,
    val rank: Long?,
    val participating: Boolean,
)

@Serializable
internal data class PressRaceOvertakes(
    val passed: Long,
    val passedBy: Long,
)

@Serializable
internal data class PressRaceSnapshot(
    val serverGeneration: String,
    val connectionGeneration: String,
    val date: String,
    val snapshotVersion: String,
    val generatedAt: String,
    val view: PressRaceView,
    val activeCount: Long,
    val me: PressRaceParticipant,
    val nearby: List<PressRaceParticipant>,
    val top100: List<PressRaceParticipant>,
    val baseline: Boolean,
    val overtakes: PressRaceOvertakes,
)

internal sealed interface PressRaceMessage {
    data class Ready(
        val value: PressRaceReady,
    ) : PressRaceMessage

    data class Snapshot(
        val value: PressRaceSnapshot,
    ) : PressRaceMessage

    data class Error(
        val code: String,
        val server: String,
        val connection: String,
    ) : PressRaceMessage
}

/** JSON is confined to the session boundary; neither transport nor screens interpret message strings. */
internal object PressRaceProtocol {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Serializable
    private data class Init(
        val view: PressRaceView,
        val v: Int = 1,
        val type: String = "session.init",
        val pending: List<PressRacePendingInput> = emptyList(),
    )

    @Serializable
    private data class PressRacePendingInput(
        val requestId: String,
        val connectionGeneration: String,
        val counts: Map<String, Int>,
    )

    @Serializable
    private data class Change(
        val view: PressRaceView,
        val v: Int = 1,
        val type: String = "view.change",
    )

    fun initialize(view: PressRaceView): String = json.encodeToString(Init(view))

    fun changeView(view: PressRaceView): String = json.encodeToString(Change(view))

    fun decode(text: String): PressRaceMessage {
        val root = json.decodeFromString<JsonObject>(text)
        val version = root["v"]?.jsonPrimitive
        require(version != null && !version.isString && version.intOrNull == 1)
        return when (root["type"]?.jsonPrimitive?.content) {
            "session.ready" -> {
                PressRaceMessage.Ready(json.decodeFromJsonElement(PressRaceReady.serializer(), root))
            }

            "state.snapshot" -> {
                PressRaceMessage.Snapshot(json.decodeFromJsonElement(PressRaceSnapshot.serializer(), root))
            }

            "protocol.error" -> {
                PressRaceMessage.Error(
                    root.getValue("code").jsonPrimitive.content,
                    root.getValue("serverGeneration").jsonPrimitive.content,
                    root.getValue("connectionGeneration").jsonPrimitive.content,
                )
            }

            else -> {
                error("Unsupported press race message")
            }
        }
    }
}

/** Decimal comparison avoids Float rounding or overflow for JSON string versions. */
internal fun compareSnapshotVersions(
    left: String,
    right: String,
): Int {
    fun normalize(value: String): String {
        require(value.isNotEmpty() && value.all { it in '0'..'9' })
        return value.trimStart('0').ifEmpty { "0" }
    }
    val a = normalize(left)
    val b = normalize(right)
    return if (a.length == b.length) a.compareTo(b) else a.length.compareTo(b.length)
}
