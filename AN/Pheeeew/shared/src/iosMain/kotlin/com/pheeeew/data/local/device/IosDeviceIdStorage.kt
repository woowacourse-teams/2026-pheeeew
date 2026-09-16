package com.pheeeew.data.local.device

import platform.Foundation.NSUserDefaults
import kotlin.uuid.Uuid

class IosDeviceIdStorage(
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DeviceIdStorage {
    override fun getOrCreate(): String {
        userDefaults.stringForKey(DEVICE_ID_KEY)?.let { return it }

        return Uuid.random().toString().also { deviceId ->
            userDefaults.setObject(deviceId, forKey = DEVICE_ID_KEY)
        }
    }

    private companion object {
        const val DEVICE_ID_KEY = "pheeeew_device_id"
    }
}
