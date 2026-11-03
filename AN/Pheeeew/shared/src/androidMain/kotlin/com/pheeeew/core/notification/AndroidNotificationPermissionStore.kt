package com.pheeeew.core.notification

import android.content.Context

/** Persists observed decisions, never an attempted request as evidence of denial. */
internal class AndroidNotificationPermissionStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): AndroidNotificationPermissionHistory = synchronized(lock) { readHistory() }

    fun <T> update(block: (AndroidNotificationPermissionHistory) -> T): T =
        synchronized(lock) {
            val history = readHistory()
            val result = block(history)
            save(history)
            result
        }

    private fun readHistory(): AndroidNotificationPermissionHistory =
        AndroidNotificationPermissionHistory(
            hasObservedRationale = preferences.getBoolean(KEY_HAS_OBSERVED_RATIONALE, false),
            hasBeenAuthorized = preferences.getBoolean(KEY_HAS_BEEN_AUTHORIZED, false),
        )

    private fun save(history: AndroidNotificationPermissionHistory) {
        preferences
            .edit()
            .putBoolean(KEY_HAS_OBSERVED_RATIONALE, history.hasObservedRationale)
            .putBoolean(KEY_HAS_BEEN_AUTHORIZED, history.hasBeenAuthorized)
            .apply()
    }

    fun reset() =
        synchronized(lock) {
            preferences
                .edit()
                .remove("has-requested-post-notifications")
                .remove(KEY_HAS_OBSERVED_RATIONALE)
                .remove(KEY_HAS_BEEN_AUTHORIZED)
                .apply()
        }

    private companion object {
        val lock = Any()
        const val PREFERENCES_NAME = "foundation-notification-permission"
        const val KEY_HAS_OBSERVED_RATIONALE = "has-observed-notification-rationale"
        const val KEY_HAS_BEEN_AUTHORIZED = "has-been-authorized"
    }
}
