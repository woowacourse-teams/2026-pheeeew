package com.pheeeew.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground receipt is separate from the click inbox: displaying a notice must not consume a tap. */
object CollectionNotificationEvents {
    data class Event(
        val revision: Long,
        val click: NotificationClick,
    )

    private val mutableEvent = MutableStateFlow<Event?>(null)
    val event = mutableEvent.asStateFlow()
    private var revision = 0L
    private val received = linkedSetOf<String>()

    fun acknowledge(revision: Long) {
        if (mutableEvent.value?.revision == revision) mutableEvent.value = null
    }

    fun receive(
        data: Map<String, String>,
        requestId: String,
    ): Boolean {
        val click = parseNotificationClick(data, requestId)
        if (click.target !is NotificationClickTarget.CollectionClaimable) return false
        if (!received.add(click.notificationId)) return true
        if (received.size > 256) received.remove(received.first())
        mutableEvent.value = Event(++revision, click)
        return true
    }
}
