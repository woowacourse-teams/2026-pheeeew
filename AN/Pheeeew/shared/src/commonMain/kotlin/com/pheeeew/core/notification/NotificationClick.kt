package com.pheeeew.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** Access on the UI thread. The latest user intent wins while startup gates are visible. */
class NotificationClickInbox {
    private val handledIds = linkedSetOf<String>()
    private var ready = false
    private var waiting: NotificationClick? = null
    private val mutablePendingClick = MutableStateFlow<NotificationClick?>(null)
    val pendingClick: StateFlow<NotificationClick?> = mutablePendingClick.asStateFlow()

    fun receive(click: NotificationClick) {
        if (!handledIds.add(click.notificationId)) return
        if (handledIds.size > MAX_REMEMBERED_IDS) handledIds.remove(handledIds.first())
        waiting = click
        mutablePendingClick.value = if (ready) click else null
    }

    fun setReady(value: Boolean) {
        ready = value
        mutablePendingClick.value = if (value) waiting else null
    }

    /** A feature acknowledges only after it has handled the target, never just on receipt. */
    fun acknowledge(notificationId: String) {
        if (waiting?.notificationId != notificationId) return
        waiting = null
        mutablePendingClick.value = null
    }

    private companion object {
        const val MAX_REMEMBERED_IDS = 256
    }
}
