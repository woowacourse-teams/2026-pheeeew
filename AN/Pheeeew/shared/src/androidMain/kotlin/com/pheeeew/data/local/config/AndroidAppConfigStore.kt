package com.pheeeew.data.local.config

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAppConfigStore(
    context: Context,
    environment: String,
) : AppConfigStore {
    private val preferences = context.getSharedPreferences("app_config_v1_android_$environment", Context.MODE_PRIVATE)

    override suspend fun read(): String? = withContext(Dispatchers.IO) { preferences.getString("snapshot", null) }

    override suspend fun clear() =
        withContext(Dispatchers.IO) {
            check(preferences.edit().remove("snapshot").commit())
        }

    override suspend fun write(value: String) =
        withContext(Dispatchers.IO) {
            check(preferences.edit().putString("snapshot", value).commit())
        }
}
