package com.pheeeew.feature.screens.group.create

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps editable drafts in this app process; only unresolved server operations survive a restart. */
class ProcessGroupCreateSessionStore(
    private val durableStore: GroupCreateSessionStore,
) : GroupCreateSessionStore {
    private val mutex = Mutex()
    private var loaded = false
    private var snapshot: GroupCreateSessionSnapshot? = null
    private var durableSnapshot: GroupCreateSessionSnapshot? = null

    override suspend fun read(): GroupCreateSessionSnapshot? =
        mutex.withLock {
            if (!loaded) {
                val previous = durableStore.read()
                val restored = previous.pendingOnly()
                // Remove drafts written by older app versions before exposing a new session.
                if (previous != restored) durableStore.write(restored)
                durableSnapshot = restored
                snapshot = restored
                loaded = true
            }
            snapshot
        }

    override suspend fun write(snapshot: GroupCreateSessionSnapshot?) {
        mutex.withLock {
            val durable = snapshot.pendingOnly()
            if (!loaded || durable != durableSnapshot) durableStore.write(durable)
            durableSnapshot = durable
            this.snapshot = snapshot
            loaded = true
        }
    }

    override suspend fun clearCorruptedDraftIfSafe(): Boolean =
        mutex.withLock {
            if (!durableStore.clearCorruptedDraftIfSafe()) return@withLock false
            snapshot = null
            durableSnapshot = null
            loaded = true
            true
        }

    private fun GroupCreateSessionSnapshot?.pendingOnly(): GroupCreateSessionSnapshot? =
        this?.takeIf { it.pendingOperation != null }?.copy(
            draft = PersistedGroupCreateDraft(),
            draftUpdatedAtEpochMillis = null,
        )
}
