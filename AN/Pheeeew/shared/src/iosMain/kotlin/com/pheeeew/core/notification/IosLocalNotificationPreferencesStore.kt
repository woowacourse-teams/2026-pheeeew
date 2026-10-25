package com.pheeeew.core.notification

import platform.Foundation.NSUserDefaults

/** UI preview preferences only; this does not change backend push delivery. */
class IosLocalNotificationPreferencesStore : NotificationPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun read(): NotificationPreferences =
        NotificationPreferences(
            collectionEnabled = value(NotificationKind.COLLECTION),
            rankDropEnabled = value(NotificationKind.RANK_DROP),
        )

    override suspend fun update(
        kind: NotificationKind,
        enabled: Boolean,
    ): NotificationPreferences {
        defaults.setBool(enabled, forKey = key(kind))
        return read()
    }

    private fun value(kind: NotificationKind): Boolean =
        if (defaults.objectForKey(key(kind)) == null) true else defaults.boolForKey(key(kind))

    private fun key(kind: NotificationKind): String = "notification_preview_${kind.name}"
}
