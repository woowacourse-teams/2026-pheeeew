package com.pheeeew.feature.screens.group.create

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupCreateSessionStoreTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `supported corrupted draft without pending operation can be discarded`() {
        val raw = snapshot(draftShape = "UNKNOWN_SHAPE")

        assertTrue(canDiscardCorruptedGroupCreateDraft(raw, json))
    }

    @Test
    fun `pending operation is never discarded even when draft is corrupted`() {
        val pendingOperation = """{"ownerInstanceId":"device","sequence":1,"draft":{}}"""
        val raw =
            snapshot(
                draftShape = "UNKNOWN_SHAPE",
                pendingOperation = pendingOperation,
            )

        assertFalse(canDiscardCorruptedGroupCreateDraft(raw, json))
    }

    @Test
    fun `malformed JSON and unsupported schema are not discarded`() {
        assertFalse(canDiscardCorruptedGroupCreateDraft("{broken", json))
        assertFalse(canDiscardCorruptedGroupCreateDraft(snapshot(schemaVersion = 99), json))
    }

    @Test
    fun `valid draft session is not treated as corrupted`() {
        assertFalse(canDiscardCorruptedGroupCreateDraft(snapshot(draftShape = "CIRCLE"), json))
    }

    @Test
    fun `new draft is public while legacy stored draft remains private`() {
        kotlin.test.assertEquals(
            com.pheeeew.feature.screens.group.model.GroupJoinType.PUBLIC,
            GroupCreateUiState().draft.joinType,
        )
        val legacy = json.decodeFromString(GroupCreateSessionSnapshot.serializer(), snapshot())
        kotlin.test.assertEquals(com.pheeeew.feature.screens.group.model.GroupJoinType.PRIVATE, legacy.draft?.joinType)
        com.pheeeew.feature.screens.group.model.GroupJoinType.entries.forEach { type ->
            val saved = legacy.copy(draft = legacy.draft!!.copy(joinType = type))
            val restored =
                json.decodeFromString(
                    GroupCreateSessionSnapshot.serializer(),
                    json.encodeToString(GroupCreateSessionSnapshot.serializer(), saved),
                )
            kotlin.test.assertEquals(type, restored.draft?.joinType)
        }
    }

    private fun snapshot(
        schemaVersion: Int = GroupCreateSessionSnapshot.CURRENT_SCHEMA_VERSION,
        draftShape: String = "CIRCLE",
        pendingOperation: String = "null",
    ): String =
        """{"schemaVersion":$schemaVersion,"draft":{"name":"draft","description":"","stampLabel":"","stampShape":"$draftShape","stampFillArgb":1,"stampTextArgb":2},"draftUpdatedAtEpochMillis":1000,"pendingOperation":$pendingOperation}"""
}
