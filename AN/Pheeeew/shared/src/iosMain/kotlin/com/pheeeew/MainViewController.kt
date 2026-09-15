package com.pheeeew

import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.di.SighModule
import com.pheeeew.di.createIosLocationDependencies
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-"
        val apiBaseUrl = NSBundle.mainBundle.infoDictionary?.get("API_BASE_URL") as? String ?: ""
        val userDefaults = NSUserDefaults.standardUserDefaults
        val locationDependencies = createIosLocationDependencies()
        val sighDependencies = SighModule.create(ApiConfig(baseUrl = apiBaseUrl))
        App(
            appVersion = appVersion,
            hasCompletedOnboarding = userDefaults.boolForKey(KEY_ONBOARDING_COMPLETED),
            onOnboardingCompleted = {
                userDefaults.setBool(true, forKey = KEY_ONBOARDING_COMPLETED)
            },
            locationDependencies = locationDependencies,
            sighRepository = sighDependencies.repository,
            createSigh = sighDependencies.createSigh,
            mapPerformanceLogger = {},
        )
    }

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
