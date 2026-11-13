package com.pheeeew.core.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAppSettingsLauncher(
    context: Context,
) : AppSettingsLauncher {
    private val applicationContext = context.applicationContext

    override suspend fun openAppSettings(): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val intent =
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", applicationContext.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            open(intent)
        }

    override suspend fun openLocationSettings(): Boolean =
        withContext(Dispatchers.Main.immediate) {
            open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

    private fun open(intent: Intent): Boolean =
        try {
            applicationContext.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
