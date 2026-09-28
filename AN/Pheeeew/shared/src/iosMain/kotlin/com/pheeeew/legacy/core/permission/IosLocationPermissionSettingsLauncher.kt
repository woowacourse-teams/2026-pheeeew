package com.pheeeew.legacy.core.permission

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

class IosLocationPermissionSettingsLauncher : LocationPermissionSettingsLauncher {
    override val canOpenLocationSettings: Boolean = false

    override val locationServicesInstruction: String = "설정에서 위치 서비스를 켜주세요."

    override val locationServicesDialogInstruction: String =
        "설정 > 개인정보 보호 및 보안 > 위치 서비스에서 위치 서비스를 켜주세요."

    override suspend fun openAppSettings(): Boolean =
        withContext(Dispatchers.Main) {
            openSettingsUrl(UIApplicationOpenSettingsURLString)
        }

    override suspend fun openLocationSettings(): Boolean = false

    private suspend fun openSettingsUrl(urlString: String): Boolean {
        val settingsUrl = NSURL(string = urlString)
        val application = UIApplication.sharedApplication

        return suspendCancellableCoroutine { continuation ->
            application.openURL(
                settingsUrl,
                options = emptyMap<Any?, Any>(),
                completionHandler = { opened ->
                    if (continuation.isActive) continuation.resume(opened)
                },
            )
        }
    }
}
