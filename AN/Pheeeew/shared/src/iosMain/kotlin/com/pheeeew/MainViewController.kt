package com.pheeeew

import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.data.local.device.InMemoryAccessTokenStore
import com.pheeeew.di.SighModule
import com.pheeeew.di.createIosDeviceRegistrationDependencies
import com.pheeeew.di.createIosLocationDependencies
import platform.Foundation.NSBundle

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-"
        val apiBaseUrl = NSBundle.mainBundle.infoDictionary?.get("API_BASE_URL") as? String ?: ""
        val locationDependencies = createIosLocationDependencies()
        val apiConfig = ApiConfig(baseUrl = apiBaseUrl)
        val accessTokenStore = InMemoryAccessTokenStore()
        val deviceDependencies = createIosDeviceRegistrationDependencies(
            config = apiConfig,
            accessTokenStore = accessTokenStore,
        )
        val sighDependencies = SighModule.create(
            config = apiConfig,
            accessTokenStore = accessTokenStore,
            refreshAccessToken = {
                deviceDependencies.ensureRegistered().getOrNull()?.accessToken
            },
        )
        App(
            appVersion = appVersion,
            locationDependencies = locationDependencies,
            sighRepository = sighDependencies.repository,
            createSigh = sighDependencies.createSigh,
            mapPerformanceLogger = {},
            ensureDeviceRegistered = deviceDependencies.ensureRegistered,
        )
    }
