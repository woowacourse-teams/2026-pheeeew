package com.pheeeew.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Access on the UI thread. The latest user intent wins while startup gates are visible. */
class NotificationClickInbox {
    private val handledIds = linkedSetOf<String>()
    private var ready = false
    private var waiting: NotificationClick? = null
    private val mutablePendingClick = MutableStateFlow<NotificationClick?>(null)
    val pendingClick: StateFlow<NotificationClick?> = mutablePendingClick.asStateFlow()

    fun receive(click: NotificationClick) {
        if (!handledIds.add(click.notificationId)) return
        if (handledIds.size > MAX_REMEMBERED_IDS) handledIds.remove(handledIds.first())
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
        waiting = null
        mutablePendingClick.value = null
    }

    private companion object {
        const val MAX_REMEMBERED_IDS = 256
    }
}
