package com.pheeeew.core.permission

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

class IosAppSettingsLauncher : AppSettingsLauncher {
    override suspend fun openAppSettings(): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                UIApplication.sharedApplication.openURL(
                    NSURL(string = UIApplicationOpenSettingsURLString),
                    options = emptyMap<Any?, Any>(),
                ) { opened ->
                    if (continuation.isActive) continuation.resume(opened)
                }
            }
        }

    override suspend fun openLocationSettings(): Boolean = openAppSettings()
}
