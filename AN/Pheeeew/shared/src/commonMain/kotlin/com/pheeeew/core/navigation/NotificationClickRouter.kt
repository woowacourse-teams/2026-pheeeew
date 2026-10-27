package com.pheeeew.core.navigation

import com.pheeeew.core.notification.NotificationClickTarget

internal sealed interface NotificationDestination {
    data object Map : NotificationDestination

    data object Press : NotificationDestination

    data class Collection(
        val date: String,
    ) : NotificationDestination
}

/** Translate notification intent independently of the navigation implementation. */
internal object NotificationClickRouter {
    fun destinationFor(target: NotificationClickTarget): NotificationDestination =
        when (target) {
            NotificationClickTarget.OpenApp -> NotificationDestination.Map
            NotificationClickTarget.Revisit -> NotificationDestination.Map
            is NotificationClickTarget.CollectionClaimable -> NotificationDestination.Collection(target.date)
            is NotificationClickTarget.Collection -> NotificationDestination.Map
            is NotificationClickTarget.DailyPressRanking -> NotificationDestination.Press
        }
}
