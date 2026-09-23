package com.pheeeew

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.legacy.core.monitoring.IosMonitoring
import com.pheeeew.legacy.core.network.ApiConfig
import com.pheeeew.legacy.core.network.IosConnectivityObserver
import com.pheeeew.legacy.data.local.device.InMemoryAccessTokenStore
import com.pheeeew.legacy.data.local.device.IosDeviceIdStorage
import com.pheeeew.legacy.data.remote.version.createAppVersionApi
import com.pheeeew.legacy.di.SighModule
import com.pheeeew.legacy.di.createIosDeviceRegistrationDependencies
import com.pheeeew.legacy.di.createIosLocationDependencies
import com.pheeeew.legacy.feature.map.guide.resolveFirstSighGuideCompleted
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val firstSighGuidePreferences =
            remember { resolveFirstSighGuidePreferences(NSUserDefaults.standardUserDefaults) }
        val appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-"
        val apiBaseUrl = NSBundle.mainBundle.infoDictionary?.get("API_BASE_URL") as? String ?: ""
        val userDefaults = NSUserDefaults.standardUserDefaults
        val locationDependencies = remember { createIosLocationDependencies() }
        val apiConfig = ApiConfig(baseUrl = apiBaseUrl)
        val appVersionApi = remember(apiBaseUrl) { createAppVersionApi(apiConfig, "ios") }
        val connectivityObserver = remember { IosConnectivityObserver() }
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
                    monitoring = IosMonitoring.instance,
                )
            }
        App(
            appVersion = appVersion,
            monitoring = IosMonitoring.instance,
            appVersionApi = appVersionApi,
            connectivityObserver = connectivityObserver,
            hasCompletedOnboarding = firstSighGuidePreferences.hasCompletedOnboarding,
            hasCompletedFirstSighGuide = firstSighGuidePreferences.hasCompletedFirstSighGuide,
            onOnboardingCompleted = {
                userDefaults.setBool(false, forKey = KEY_FIRST_SIGH_GUIDE_COMPLETED)
                userDefaults.setBool(true, forKey = KEY_ONBOARDING_COMPLETED)
            },
            onFirstSighGuideCompleted = {
                userDefaults.setBool(true, forKey = KEY_FIRST_SIGH_GUIDE_COMPLETED)
            },
            locationDependencies = locationDependencies,
            sighRepository = sighDependencies.repository,
            createSigh = sighDependencies.createSigh,
            blockUser = sighDependencies.blockUser,
            reportSigh = sighDependencies.reportSigh,
            mapPerformanceLogger = {},
            ensureDeviceRegistered = deviceDependencies.ensureRegistered,
        )
    }

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_FIRST_SIGH_GUIDE_COMPLETED = "first_sigh_guide_completed_v1"

private data class FirstSighGuidePreferences(
    val hasCompletedOnboarding: Boolean,
    val hasCompletedFirstSighGuide: Boolean,
)

private fun resolveFirstSighGuidePreferences(userDefaults: NSUserDefaults): FirstSighGuidePreferences {
    val hasCompletedOnboarding = userDefaults.boolForKey(KEY_ONBOARDING_COMPLETED)
    val hasStoredGuideCompletion = userDefaults.objectForKey(KEY_FIRST_SIGH_GUIDE_COMPLETED) != null
    val hasCompletedFirstSighGuide =
        resolveFirstSighGuideCompleted(
            hasCompletedOnboarding = hasCompletedOnboarding,
            storedGuideCompletion =
                if (hasStoredGuideCompletion) {
                    userDefaults.boolForKey(KEY_FIRST_SIGH_GUIDE_COMPLETED)
                } else {
                    null
                },
        )
    if (!hasStoredGuideCompletion && hasCompletedOnboarding) {
        userDefaults.setBool(true, forKey = KEY_FIRST_SIGH_GUIDE_COMPLETED)
    }
    return FirstSighGuidePreferences(hasCompletedOnboarding, hasCompletedFirstSighGuide)
}
