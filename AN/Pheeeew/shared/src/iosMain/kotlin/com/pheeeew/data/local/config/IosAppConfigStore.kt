package com.pheeeew.data.local.config

import platform.Foundation.NSUserDefaults

class IosAppConfigStore(
    environment: String,
) : AppConfigStore {
    private val key = "app_config_v1_ios_$environment"

    override suspend fun read(): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)

    override suspend fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }

    override suspend fun write(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }
}
