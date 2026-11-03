package com.pheeeew.core.notification

import android.content.Context
import android.content.SharedPreferences

/** Persists observed decisions, never an attempted request as evidence of denial. */
internal class AndroidNotificationPermissionStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): AndroidNotificationPermissionHistory = synchronized(lock) { readHistory() }

    fun <T> update(
        permissionGranted: Boolean,
        block: (AndroidNotificationPermissionHistory) -> T,
    ): T =
        synchronized(lock) {
            val history = readHistory()
            val result = block(history)
            val editor = preferences.edit()
            if (preferences.getBoolean(KEY_RESET_PENDING, false)) {
                if (permissionGranted) return@synchronized result
                editor.remove(KEY_RESET_PENDING)
            }
            save(history, editor)
            result
        }

    private fun readHistory(): AndroidNotificationPermissionHistory =
        AndroidNotificationPermissionHistory(
            hasObservedRationale = preferences.getBoolean(KEY_HAS_OBSERVED_RATIONALE, false),
            hasBeenAuthorized = preferences.getBoolean(KEY_HAS_BEEN_AUTHORIZED, false),
        )

    private fun save(
        history: AndroidNotificationPermissionHistory,
        editor: SharedPreferences.Editor,
    ) {
        editor
            .putBoolean(KEY_HAS_OBSERVED_RATIONALE, history.hasObservedRationale)
            .putBoolean(KEY_HAS_BEEN_AUTHORIZED, history.hasBeenAuthorized)
            .apply()
    }

    fun reset(permissionGranted: Boolean) =
        synchronized(lock) {
            val editor =
                preferences
                    .edit()
                    .remove("has-requested-post-notifications")
                    .remove(KEY_HAS_OBSERVED_RATIONALE)
                    .remove(KEY_HAS_BEEN_AUTHORIZED)
            if (permissionGranted) {
                editor.putBoolean(KEY_RESET_PENDING, true)
            } else {
                editor.remove(KEY_RESET_PENDING)
            }
            editor.apply()
        }

    private companion object {
        val lock = Any()
        const val PREFERENCES_NAME = "foundation-notification-permission"
        const val KEY_HAS_OBSERVED_RATIONALE = "has-observed-notification-rationale"
        const val KEY_HAS_BEEN_AUTHORIZED = "has-been-authorized"
        const val KEY_RESET_PENDING = "notification-permission-reset-pending"
    }
}
