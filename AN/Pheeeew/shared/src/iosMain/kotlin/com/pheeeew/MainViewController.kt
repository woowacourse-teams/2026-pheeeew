package com.pheeeew

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.data.local.device.InMemoryAccessTokenStore
import com.pheeeew.data.local.device.IosDeviceIdStorage
import com.pheeeew.di.SighModule
import com.pheeeew.di.createIosDeviceRegistrationDependencies
import com.pheeeew.di.createIosLocationDependencies
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-"
        val apiBaseUrl = NSBundle.mainBundle.infoDictionary?.get("API_BASE_URL") as? String ?: ""
        val userDefaults = NSUserDefaults.standardUserDefaults
        val locationDependencies = remember { createIosLocationDependencies() }
        val apiConfig = ApiConfig(baseUrl = apiBaseUrl)
        val accessTokenStore = remember { InMemoryAccessTokenStore() }
        val deviceDependencies =
            remember(apiBaseUrl) {
                createIosDeviceRegistrationDependencies(
                    config = apiConfig,
                    accessTokenStore = accessTokenStore,
                )
            }
        val sighDependencies =
            remember(apiBaseUrl, deviceDependencies) {
                SighModule.create(
                    config = apiConfig,
                    deviceIdStorage = IosDeviceIdStorage(userDefaults),
                    accessTokenStore = accessTokenStore,
                    refreshAccessToken = {
                        deviceDependencies.ensureRegistered().getOrThrow().accessToken
                    },
                )
            }
        App(
            appVersion = appVersion,
            hasCompletedOnboarding = userDefaults.boolForKey(KEY_ONBOARDING_COMPLETED),
            onOnboardingCompleted = {
                userDefaults.setBool(true, forKey = KEY_ONBOARDING_COMPLETED)
            },
            locationDependencies = locationDependencies,
            sighRepository = sighDependencies.repository,
            createSigh = sighDependencies.createSigh,
            reportSigh = sighDependencies.reportSigh,
            mapPerformanceLogger = {},
            ensureDeviceRegistered = deviceDependencies.ensureRegistered,
        )
    }

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
