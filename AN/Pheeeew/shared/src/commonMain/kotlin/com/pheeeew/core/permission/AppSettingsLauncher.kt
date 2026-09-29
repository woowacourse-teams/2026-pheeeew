package com.pheeeew.core.permission

interface AppSettingsLauncher {
    suspend fun openAppSettings(): Boolean

    suspend fun openLocationSettings(): Boolean
}
