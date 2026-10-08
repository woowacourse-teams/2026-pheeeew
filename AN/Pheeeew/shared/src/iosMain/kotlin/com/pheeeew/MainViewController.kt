package com.pheeeew

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.IosApiDependencies
import com.pheeeew.core.permission.IosAppSettingsLauncher
import com.pheeeew.data.local.group.IosGroupCreateSessionStore
import com.pheeeew.data.local.group.IosLastRecordedGroupRepository
import com.pheeeew.data.location.platform.ios.createIosLocationDependencies
import com.pheeeew.data.remote.version.AppVersionApi
import com.pheeeew.feature.screens.onboarding.WELCOME_ONBOARDING_COMPLETED_KEY
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val locationDependencies = remember { createIosLocationDependencies() }
        val onboardingPreferences = remember { NSUserDefaults.standardUserDefaults }
        val apiDependencies = remember { IosApiDependencies.instance }
        val appVersionApi = remember { AppVersionApi(apiDependencies.client.requests, "ios") }
        val appSettingsLauncher = remember { IosAppSettingsLauncher() }
        App(
            locationDependencies = locationDependencies,
            lastRecordedGroupRepository =
                remember {
                    IosLastRecordedGroupRepository(NSUserDefaults.standardUserDefaults)
                },
            groupCreateSessionStore =
                remember {
                    IosGroupCreateSessionStore(NSUserDefaults.standardUserDefaults)
                },
            apiDependencies = apiDependencies,
            appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-",
            appVersionApi = appVersionApi,
            permissionSettingsLauncher = appSettingsLauncher,
            appSettingsLauncher = appSettingsLauncher,
            hasCompletedOnboarding = onboardingPreferences.boolForKey(WELCOME_ONBOARDING_COMPLETED_KEY),
            onOnboardingCompleted = {
                onboardingPreferences.setBool(true, forKey = WELCOME_ONBOARDING_COMPLETED_KEY)
            },
        )
    }
