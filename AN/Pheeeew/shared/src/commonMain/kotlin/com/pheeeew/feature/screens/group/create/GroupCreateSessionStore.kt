package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.component.stamp.StampShapeId
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Versioned local snapshot; the pending operation keeps the exact payload that may have reached the server. */
@Serializable
data class GroupCreateSessionSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val draft: PersistedGroupCreateDraft = PersistedGroupCreateDraft(),
    val draftUpdatedAtEpochMillis: Long? = null,
    val pendingOperation: PersistedGroupCreateOperation? = null,
) {
    init {
        require(schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) {
            "지원하지 않는 그룹 생성 세션 버전입니다."
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val DRAFT_TTL_MILLIS = 60L * 60 * 1000
        private const val MIN_SUPPORTED_SCHEMA_VERSION = 1
    }
}

/**
 * Returns true only when the stored envelope is readable enough to prove that it belongs to a supported schema and
 * contains no pending create operation. Corrupt draft fields may then be discarded without losing a possibly accepted
 * POST.
 */
internal fun canDiscardCorruptedGroupCreateDraft(
    rawSnapshot: String,
    json: Json,
): Boolean {
    val envelope = runCatching { json.parseToJsonElement(rawSnapshot) as? JsonObject }.getOrNull() ?: return false
    val schemaVersion =
        envelope["schemaVersion"]?.let { value ->
            runCatching { value.jsonPrimitive.intOrNull }.getOrNull() ?: return false
        } ?: GroupCreateSessionSnapshot.CURRENT_SCHEMA_VERSION
    if (schemaVersion !in 1..GroupCreateSessionSnapshot.CURRENT_SCHEMA_VERSION) return false
    if (envelope["pendingOperation"]?.let { it !is JsonNull } == true) return false

    val snapshot = runCatching { json.decodeFromString<GroupCreateSessionSnapshot>(rawSnapshot) }.getOrNull()
    if (snapshot == null) return true

    return runCatching { StampShapeId.valueOf(snapshot.draft.stampShape) }.isFailure
}

@Serializable
data class PersistedGroupCreateDraft(
    val name: String = "",
    val description: String = "",
    val stampLabel: String = "",
    val stampShape: String = "CIRCLE",
    val stampFillArgb: Long = 0xFFA7DCCFL,
    val stampTextArgb: Long = 0xFF000000L,
)

@Serializable
data class PersistedGroupCreateOperation(
    val ownerInstanceId: String,
    val sequence: Long,
    val draft: PersistedGroupCreateDraft,
)

/** Platform storage port. Implementations must replace the serialized snapshot atomically. */
interface GroupCreateSessionStore {
    suspend fun read(): GroupCreateSessionSnapshot?

    suspend fun write(snapshot: GroupCreateSessionSnapshot?)

    /** Clears a corrupt editable draft only when the implementation can prove no pending create operation exists. */
    suspend fun clearCorruptedDraftIfSafe(): Boolean = false
}

/** Useful for previews and tests that do not need persistence. Production injects platform storage. */
object EmptyGroupCreateSessionStore : GroupCreateSessionStore {
    override suspend fun read(): GroupCreateSessionSnapshot? = null

    override suspend fun write(snapshot: GroupCreateSessionSnapshot?) = Unit
}
