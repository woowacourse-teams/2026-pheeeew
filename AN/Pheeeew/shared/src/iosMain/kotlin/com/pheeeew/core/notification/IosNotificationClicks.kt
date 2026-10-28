package com.pheeeew.core.notification

/** AppDelegate calls this on the main thread, including before Compose is created. */
object IosNotificationClicks {
    val inbox = NotificationClickInbox()

    fun receive(
        data: Map<String, String>,
        requestId: String,
    ) {
        inbox.receive(parseNotificationClick(data, requestId))
    }
}

object IosNotificationMessages {
    val inbox = NotificationMessageInbox()

    fun receive(
        data: Map<String, String>,
        messageId: String,
    ) {
        inbox.receive(NotificationMessage(messageId, data))
    }
}
