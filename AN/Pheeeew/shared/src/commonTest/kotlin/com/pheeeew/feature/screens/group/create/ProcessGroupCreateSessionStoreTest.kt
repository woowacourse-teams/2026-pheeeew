package com.pheeeew.feature.screens.group.create

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProcessGroupCreateSessionStoreTest {
    @Test
    fun sameProcessRetainsEveryDraftFieldButNewProcessStartsEmpty() =
        runTest {
            val disk = FakeDurableStore()
            val process = ProcessGroupCreateSessionStore(disk)
            val draft =
                PersistedGroupCreateDraft(
                    name = "모임",
                    description = "설명",
                    stampLabel = "도장",
                    stampShape = "SQUARE",
                    stampFillArgb = 1L,
                    stampTextArgb = 2L,
                )
            val snapshot = GroupCreateSessionSnapshot(draft = draft, draftUpdatedAtEpochMillis = 123L)
            process.write(snapshot)
            assertEquals(snapshot, process.read())
            assertNull(disk.value)
            assertNull(ProcessGroupCreateSessionStore(disk).read())
            assertEquals(snapshot, process.read())
        }

    @Test
    fun oldPersistedDraftIsRemovedAndNeverRestored() =
        runTest {
            val disk = FakeDurableStore(GroupCreateSessionSnapshot(draft = PersistedGroupCreateDraft(name = "과거 초안")))
            assertNull(ProcessGroupCreateSessionStore(disk).read())
            assertNull(disk.value)
        }

    @Test
    fun unresolvedRequestPreservesExactPayloadAcrossProcesses() =
        runTest {
            val disk = FakeDurableStore()
            val operation = PersistedGroupCreateOperation("owner", 1L, PersistedGroupCreateDraft(name = "전송한 모임"))
            val snapshot = GroupCreateSessionSnapshot(draft = operation.draft, pendingOperation = operation)
            val process = ProcessGroupCreateSessionStore(disk)
            process.write(snapshot)
            assertEquals(snapshot, process.read())
            assertEquals(PersistedGroupCreateDraft(), disk.value?.draft)
            val restored = ProcessGroupCreateSessionStore(disk).read()
            assertEquals(operation, restored?.pendingOperation)
            assertNull(restored?.draftUpdatedAtEpochMillis)
        }

    @Test
    fun clearRemovesBothInMemoryDraftAndDurableOperation() =
        runTest {
            val disk = FakeDurableStore()
            val process = ProcessGroupCreateSessionStore(disk)
            val operation = PersistedGroupCreateOperation("owner", 1L, PersistedGroupCreateDraft(name = "모임"))
            process.write(GroupCreateSessionSnapshot(pendingOperation = operation))
            process.write(null)
            assertNull(process.read())
            assertNull(ProcessGroupCreateSessionStore(disk).read())
        }

    @Test
    fun failedDurableWriteDoesNotReplaceCurrentState() =
        runTest {
            val disk = FakeDurableStore()
            val process = ProcessGroupCreateSessionStore(disk)
            val operation = PersistedGroupCreateOperation("owner", 1L, PersistedGroupCreateDraft(name = "유지"))
            val snapshot = GroupCreateSessionSnapshot(draft = operation.draft, pendingOperation = operation)
            process.write(snapshot)
            disk.failWrite = true
            assertFailsWith<IllegalStateException> { process.write(null) }
            assertEquals(snapshot, process.read())
        }

    private class FakeDurableStore(
        var value: GroupCreateSessionSnapshot? = null,
    ) : GroupCreateSessionStore {
        var failWrite = false

        override suspend fun read() = value

        override suspend fun write(snapshot: GroupCreateSessionSnapshot?) {
            check(!failWrite)
            value = snapshot
        }
    }
}
