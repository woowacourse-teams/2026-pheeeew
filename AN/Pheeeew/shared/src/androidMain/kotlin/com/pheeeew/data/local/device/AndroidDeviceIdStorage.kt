package com.pheeeew.data.local.device

import android.content.Context
import kotlin.uuid.Uuid

class AndroidDeviceIdStorage(
    context: Context,
) : DeviceIdStorage {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getOrCreate(): String {
        preferences.getString(DEVICE_ID_KEY, null)?.let { return it }

        return Uuid.random().toString().also { deviceId ->
            preferences.edit().putString(DEVICE_ID_KEY, deviceId).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "device_identity"
        const val DEVICE_ID_KEY = "device_id"
    }
}
