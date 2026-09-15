package com.pheeeew

import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.di.SighModule
import com.pheeeew.di.createIosLocationDependencies
import com.pheeeew.feature.map.guide.resolveFirstSighGuideCompleted
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-"
        val apiBaseUrl = NSBundle.mainBundle.infoDictionary?.get("API_BASE_URL") as? String ?: ""
        val userDefaults = NSUserDefaults.standardUserDefaults
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
        val locationDependencies = createIosLocationDependencies()
        val sighDependencies = SighModule.create(ApiConfig(baseUrl = apiBaseUrl))
        App(
            appVersion = appVersion,
            hasCompletedOnboarding = hasCompletedOnboarding,
            hasCompletedFirstSighGuide = hasCompletedFirstSighGuide,
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
            mapPerformanceLogger = {},
        )
    }

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_FIRST_SIGH_GUIDE_COMPLETED = "first_sigh_guide_completed_v1"
