package com.pheeeew.core.notification

/** Field names follow the notification contract draft; screen-specific routing is handled by each feature. */
fun parseNotificationClick(
    data: Map<String, String>,
    requestId: String,
): NotificationClick {
    fun field(name: String): String? = data[name]?.trim()?.takeIf { it.isNotEmpty() }

    val notificationId = field("notificationId") ?: requestId
    val target =
        if (field("schemaVersion") !in listOf(null, "1")) {
            NotificationClickTarget.OpenApp
        } else {
            when (field("type")) {
                "COLLECTION_ISSUED" -> {
                    field("collectionId")?.let(NotificationClickTarget::Collection)
                        ?: NotificationClickTarget.OpenApp
                }

                "RANK_DROPPED" -> {
                    val rankingType = field("rankingType")
                    val periodId = field("periodId")
                    val previousRank = field("previousRank")?.toIntOrNull()?.takeIf { it > 0 }
                    val currentRank = field("currentRank")?.toIntOrNull()?.takeIf { it > 0 }
                    if (rankingType != null && periodId != null && previousRank != null && currentRank != null) {
                        NotificationClickTarget.Ranking(
                            rankingType = rankingType,
                            periodId = periodId,
                            groupId = field("groupId"),
                            previousRank = previousRank,
                            currentRank = currentRank,
                        )
                    } else {
                        NotificationClickTarget.OpenApp
                    }
                }

                else -> {
                    NotificationClickTarget.OpenApp
                }
            }
        }
    return NotificationClick(notificationId, target)
}
