package com.pheeeew.core.notification

/** AppDelegate calls this on the main thread, including before Compose is created. */
object IosNotificationClicks {
    val inbox = NotificationClickInbox()

    fun receiveForeground(
        data: Map<String, String>,
        requestId: String,
    ): Boolean = CollectionNotificationEvents.receive(data, requestId)

    fun receive(
        data: Map<String, String>,
        requestId: String,
    ) {
        inbox.receive(parseNotificationClick(data, requestId))
    }
}
