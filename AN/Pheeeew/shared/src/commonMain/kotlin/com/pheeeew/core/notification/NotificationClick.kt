package com.pheeeew.core.notification

sealed interface NotificationClickTarget {
    data object OpenApp : NotificationClickTarget

    data object Revisit : NotificationClickTarget

    data class CollectionClaimable(
        val date: String,
    ) : NotificationClickTarget

    data class Collection(
        val collectionId: String,
    ) : NotificationClickTarget

    data class DailyPressRanking(
        val date: String,
        val previousRank: Int,
        val currentRank: Int,
    ) : NotificationClickTarget
}

data class NotificationClick(
    val notificationId: String,
    val target: NotificationClickTarget,
)
