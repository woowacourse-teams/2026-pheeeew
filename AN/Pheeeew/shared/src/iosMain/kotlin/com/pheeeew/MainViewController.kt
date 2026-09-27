package com.pheeeew

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.IosApiDependencies
import com.pheeeew.data.location.platform.ios.createIosLocationDependencies
import com.pheeeew.feature.screens.onboarding.WELCOME_ONBOARDING_COMPLETED_KEY
import platform.Foundation.NSUserDefaults

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val locationDependencies = remember { createIosLocationDependencies() }
        val onboardingPreferences = remember { NSUserDefaults.standardUserDefaults }
        App(
            locationDependencies = locationDependencies,
            apiDependencies = IosApiDependencies.instance,
            hasCompletedOnboarding = onboardingPreferences.boolForKey(WELCOME_ONBOARDING_COMPLETED_KEY),
            onOnboardingCompleted = {
                onboardingPreferences.setBool(true, forKey = WELCOME_ONBOARDING_COMPLETED_KEY)
            },
        )
    }
