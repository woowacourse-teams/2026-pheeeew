package com.pheeeew.core.notification

import com.pheeeew.core.permission.AppSettingsLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class IosNotificationPermissionController(
    private val settingsLauncher: AppSettingsLauncher,
) : NotificationPermissionController {
    override suspend fun authorization(): NotificationAuthorization =
        suspendCancellableCoroutine { continuation ->
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.getNotificationSettingsWithCompletionHandler { settings ->
                val status =
                    when (settings?.authorizationStatus) {
                        UNAuthorizationStatusNotDetermined -> NotificationAuthorization.NOT_DETERMINED
                        UNAuthorizationStatusDenied -> NotificationAuthorization.DENIED
                        UNAuthorizationStatusAuthorized -> NotificationAuthorization.AUTHORIZED
                        UNAuthorizationStatusProvisional -> NotificationAuthorization.PROVISIONAL
                        UNAuthorizationStatusEphemeral -> NotificationAuthorization.EPHEMERAL
                        else -> NotificationAuthorization.UNKNOWN
                    }
                if (continuation.isActive) continuation.resume(status)
            }
        }

    override suspend fun requestAuthorization(): Unit =
        suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
            ) { _, error ->
                if (continuation.isActive) {
                    if (error != null) {
                        continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
        }

    override suspend fun openSettings(): Boolean {
        val opened =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    UIApplication.sharedApplication.openURL(
                        NSURL(string = UIApplicationOpenNotificationSettingsURLString),
                        options = emptyMap<Any?, Any>(),
                    ) { success ->
                        if (continuation.isActive) continuation.resume(success)
                    }
                }
            }
        return opened || settingsLauncher.openAppSettings()
    }
}
