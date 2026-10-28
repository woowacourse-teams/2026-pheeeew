package com.pheeeew.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationMessage(
    val id: String,
    val data: Map<String, String>,
)

/** Latest foreground message for refreshing a visible feature. System UI is owned by the platform. */
class NotificationMessageInbox {
    private val seenIds = linkedSetOf<String>()
    private val mutablePending = MutableStateFlow<NotificationMessage?>(null)
    val pending: StateFlow<NotificationMessage?> = mutablePending.asStateFlow()

    fun receive(message: NotificationMessage) {
        if (message.id.isBlank() || !seenIds.add(message.id)) return
        if (seenIds.size > MAX_REMEMBERED_IDS) seenIds.remove(seenIds.first())
        mutablePending.value = message
    }

    fun acknowledge(messageId: String) {
        if (mutablePending.value?.id == messageId) mutablePending.value = null
    }

    /** A background transition discards obsolete foreground refresh work. */
    fun clearPending() {
        mutablePending.value = null
    }

    private companion object {
        const val MAX_REMEMBERED_IDS = 256
    }
}
