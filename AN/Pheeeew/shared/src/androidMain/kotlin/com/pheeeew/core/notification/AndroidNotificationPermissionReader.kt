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
    private val permissionStore = AndroidNotificationPermissionStore(applicationContext)

    override suspend fun authorization(): NotificationAuthorization {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val authorization =
                permissionStore.update { history ->
                    history.authorization(
                        granted =
                            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED,
                        showsRationale = false,
                    )
                }
            if (authorization != NotificationAuthorization.AUTHORIZED) return authorization
        }
        return if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationAuthorization.AUTHORIZED
        } else {
            NotificationAuthorization.DENIED
        }
    }
}
