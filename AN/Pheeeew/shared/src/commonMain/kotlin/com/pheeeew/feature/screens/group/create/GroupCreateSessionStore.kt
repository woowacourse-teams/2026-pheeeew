package com.pheeeew.feature.screens.group.create

import kotlinx.serialization.Serializable

/** Versioned local snapshot; the pending operation keeps the exact payload that may have reached the server. */
@Serializable
data class GroupCreateSessionSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val draft: PersistedGroupCreateDraft = PersistedGroupCreateDraft(),
    val pendingOperation: PersistedGroupCreateOperation? = null,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "지원하지 않는 그룹 생성 세션 버전입니다." }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
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
}

/** Useful for previews and tests that do not need persistence. Production injects platform storage. */
object EmptyGroupCreateSessionStore : GroupCreateSessionStore {
    override suspend fun read(): GroupCreateSessionSnapshot? = null

    override suspend fun write(snapshot: GroupCreateSessionSnapshot?) = Unit
}
