package com.pheeeew.core.notification

sealed interface NotificationClickTarget {
    data object OpenApp : NotificationClickTarget

    data class Collection(
        val collectionId: String,
    ) : NotificationClickTarget

    data class Ranking(
        val rankingType: String,
        val periodId: String,
        val groupId: String?,
        val previousRank: Int,
        val currentRank: Int,
    ) : NotificationClickTarget
}

data class NotificationClick(
    val notificationId: String,
    val target: NotificationClickTarget,
)
