package com.pheeeew.core.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/** Process-safe authorization snapshot. It never retains an Activity or launches UI. */
class AndroidNotificationPermissionReader(
    context: Context,
) : NotificationAuthorizationProvider {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun authorization(): NotificationAuthorization {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return if (preferences.getBoolean(KEY_HAS_REQUESTED, false)) {
                NotificationAuthorization.DENIED
            } else {
                NotificationAuthorization.NOT_DETERMINED
            }
        }
        return if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationAuthorization.AUTHORIZED
        } else {
            NotificationAuthorization.DENIED
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "foundation-notification-permission"
        const val KEY_HAS_REQUESTED = "has-requested-post-notifications"
    }
}
