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
                "REVISIT_REMINDER" -> {
                    NotificationClickTarget.Revisit
                }

                "COLLECTION_CLAIMABLE", "COLLECTION_CLAIM_REMINDER" -> {
                    field(
                        "date",
                    )?.takeIf(::isCollectionNotificationDate)?.let(NotificationClickTarget::CollectionClaimable)
                        ?: NotificationClickTarget.OpenApp
                }

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

internal fun isCollectionNotificationDate(value: String): Boolean {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) return false
    val year = value.substring(0, 4).toInt()
    val month = value.substring(5, 7).toInt()
    val day = value.substring(8, 10).toInt()
    if (year < 1 || month !in 1..12) return false
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val days =
        when (month) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    return day in 1..days
}
