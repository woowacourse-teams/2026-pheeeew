package com.pheeeew.core.notification

enum class NotificationKind { COLLECTION, RANK_DROP }

data class NotificationPreferences(
    val collectionEnabled: Boolean = true,
    val rankDropEnabled: Boolean = true,
) {
    fun enabled(kind: NotificationKind): Boolean =
        when (kind) {
            NotificationKind.COLLECTION -> collectionEnabled
            NotificationKind.RANK_DROP -> rankDropEnabled
        }
}

/** Device preferences are independent of the operating system permission. */
interface NotificationPreferencesStore {
    suspend fun read(): NotificationPreferences

    suspend fun replace(preferences: NotificationPreferences): NotificationPreferences {
        update(NotificationKind.COLLECTION, preferences.collectionEnabled)
        return update(NotificationKind.RANK_DROP, preferences.rankDropEnabled)
    }

    suspend fun update(
        kind: NotificationKind,
        enabled: Boolean,
    ): NotificationPreferences
}

fun NotificationAuthorization?.allowsNotifications(): Boolean =
    this == NotificationAuthorization.AUTHORIZED ||
        this == NotificationAuthorization.PROVISIONAL || this == NotificationAuthorization.EPHEMERAL
