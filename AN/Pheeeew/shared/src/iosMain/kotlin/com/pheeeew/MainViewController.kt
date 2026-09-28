package com.pheeeew

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.IosApiDependencies
import com.pheeeew.core.permission.IosAppSettingsLauncher
import com.pheeeew.data.local.group.IosLastRecordedGroupRepository
import com.pheeeew.data.location.platform.ios.createIosLocationDependencies
import com.pheeeew.feature.screens.onboarding.WELCOME_ONBOARDING_COMPLETED_KEY
import com.pheeeew.legacy.core.network.IosConnectivityObserver
import com.pheeeew.legacy.core.permission.IosLocationPermissionSettingsLauncher
import com.pheeeew.legacy.data.remote.version.createAppVersionApi
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import com.pheeeew.legacy.core.network.ApiConfig as LegacyApiConfig

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val locationDependencies = remember { createIosLocationDependencies() }
        val onboardingPreferences = remember { NSUserDefaults.standardUserDefaults }
        val appVersionApi =
            remember {
                val apiBaseUrl =
                    (NSBundle.mainBundle.infoDictionary?.get("API_BASE_URL") as? String)
                        ?.takeIf { it.isNotBlank() && !it.contains("$(") }
                        ?: error("Missing or unresolved build setting: API_BASE_URL")
                createAppVersionApi(LegacyApiConfig(apiBaseUrl), "ios")
            }
        App(
            locationDependencies = locationDependencies,
            connectivityObserver = remember { IosConnectivityObserver() },
            lastRecordedGroupRepository =
                remember {
                    IosLastRecordedGroupRepository(NSUserDefaults.standardUserDefaults)
                },
            apiDependencies = IosApiDependencies.instance,
            appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-",
            appVersionApi = appVersionApi,
            permissionSettingsLauncher = IosLocationPermissionSettingsLauncher(),
            appSettingsLauncher = IosAppSettingsLauncher(),
            hasCompletedOnboarding = onboardingPreferences.boolForKey(WELCOME_ONBOARDING_COMPLETED_KEY),
            onOnboardingCompleted = {
                onboardingPreferences.setBool(true, forKey = WELCOME_ONBOARDING_COMPLETED_KEY)
            },
        )
    }
