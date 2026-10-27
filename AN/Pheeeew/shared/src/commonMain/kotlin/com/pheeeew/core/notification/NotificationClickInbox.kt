package com.pheeeew.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Access on the UI thread. The latest user intent wins while startup gates are visible. */
class NotificationClickInbox(
    private val store: NotificationClickStore? = null,
) {
    private val restored = store?.read() ?: NotificationClickSnapshot()
    private val handledIds = restored.rememberedIds.takeLast(MAX_REMEMBERED_IDS).toCollection(linkedSetOf())
    private var ready = false
    private var waiting: NotificationClick? = restored.pending
    private val mutablePendingClick = MutableStateFlow<NotificationClick?>(null)
    val pendingClick: StateFlow<NotificationClick?> = mutablePendingClick.asStateFlow()

    fun receive(click: NotificationClick) {
        if (!handledIds.add(click.notificationId)) return
        if (handledIds.size > MAX_REMEMBERED_IDS) handledIds.remove(handledIds.first())
        store?.write(NotificationClickSnapshot(handledIds.toList(), click))
        waiting = click
        mutablePendingClick.value = if (ready) click else null
    }

    fun setReady(value: Boolean) {
        ready = value
        mutablePendingClick.value = if (value) waiting else null
    }

    /** A feature acknowledges only after it has handled the target, never just on receipt. */
    fun acknowledge(notificationId: String) {
        if (waiting?.notificationId != notificationId) return
        store?.write(NotificationClickSnapshot(handledIds.toList(), null))
        waiting = null
        mutablePendingClick.value = null
    }

    private companion object {
        const val MAX_REMEMBERED_IDS = 256
    }
}

/** Persist the latest intent and deduplication history together, including superseded intents. */
data class NotificationClickSnapshot(
    val rememberedIds: List<String> = emptyList(),
    val pending: NotificationClick? = null,
)

interface NotificationClickStore {
    fun read(): NotificationClickSnapshot

    fun write(snapshot: NotificationClickSnapshot)
}
