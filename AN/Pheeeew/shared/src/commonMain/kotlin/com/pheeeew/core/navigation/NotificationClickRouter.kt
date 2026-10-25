package com.pheeeew.core.navigation

import com.pheeeew.core.notification.NotificationClickTarget

internal sealed interface NotificationDestination {
    data object Map : NotificationDestination
}

/** All types currently open the map. Enable dedicated destinations here when their screens are ready. */
internal object NotificationClickRouter {
    fun destinationFor(target: NotificationClickTarget): NotificationDestination =
        when (target) {
            NotificationClickTarget.OpenApp -> NotificationDestination.Map
            is NotificationClickTarget.Collection -> NotificationDestination.Map
            is NotificationClickTarget.Ranking -> NotificationDestination.Map
        }
}
