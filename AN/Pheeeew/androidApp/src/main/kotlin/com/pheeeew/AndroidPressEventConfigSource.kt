package com.pheeeew

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.pheeeew.data.config.PressEventConfigSource

internal class AndroidPressEventConfigSource : PressEventConfigSource {
    private val remoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    override fun initialize(
        parameterKey: String,
        minimumFetchIntervalSeconds: Long,
        completion: (Boolean) -> Unit,
    ) {
        // The isolated update-test package deliberately has no Firebase application configuration.
        val remote = runCatching { remoteConfig }.getOrNull()
        if (remote == null) {
            completion(false)
            return
        }
        val settings =
            FirebaseRemoteConfigSettings
                .Builder()
                .setMinimumFetchIntervalInSeconds(minimumFetchIntervalSeconds)
                .setFetchTimeoutInSeconds(10)
                .build()
        remote
            .setConfigSettingsAsync(settings)
            .onSuccessTask { remote.setDefaultsAsync(mapOf(parameterKey to false)) }
            .onSuccessTask { remote.ensureInitialized() }
            .addOnCompleteListener { completion(it.isSuccessful) }
    }

    override fun activatedBoolean(parameterKey: String): Boolean = remoteConfig.getBoolean(parameterKey)

    override fun fetchAndActivate(
        forceRefresh: Boolean,
        completion: (Boolean) -> Unit,
    ) {
        // Task.result is "changed", so use isSuccessful even when result is false.
        val task =
            if (forceRefresh) {
                remoteConfig.fetch(0L).onSuccessTask { remoteConfig.activate() }
            } else {
                remoteConfig.fetchAndActivate()
            }
        task.addOnCompleteListener { completion(it.isSuccessful) }
    }
}
