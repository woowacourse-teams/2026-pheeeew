package com.pheeeew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.pheeeew.core.di.AndroidApiDependencies
import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.permission.AndroidAppSettingsLauncher
import com.pheeeew.data.local.group.AndroidGroupCreateSessionStore
import com.pheeeew.data.local.group.AndroidLastRecordedGroupRepository
import com.pheeeew.data.location.platform.android.LocationDependenciesHolder
import com.pheeeew.data.location.platform.android.createAndroidLocationDependencies
import com.pheeeew.data.remote.version.AppVersionApi
import com.pheeeew.feature.screens.onboarding.WELCOME_ONBOARDING_COMPLETED_KEY

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dependenciesHolder = ViewModelProvider(this)[LocationDependenciesHolder::class.java]
        val locationDependencies =
            createAndroidLocationDependencies(
                activity = this,
                retainedDependencies = dependenciesHolder.dependencies,
            ).also { dependenciesHolder.dependencies = it }
        val onboardingPreferences = getSharedPreferences("pheeeew_preferences", MODE_PRIVATE)
        val hasCompletedOnboarding = onboardingPreferences.getBoolean(WELCOME_ONBOARDING_COMPLETED_KEY, false)
        val lastRecordedGroupRepository = AndroidLastRecordedGroupRepository(applicationContext)
        val groupCreateSessionStore = AndroidGroupCreateSessionStore(applicationContext)
        val apiDependencies =
            AndroidApiDependencies.get(
                applicationContext,
                DeviceSessionBuildConfig(
                    BuildConfig.DEBUG,
                    BuildConfig.DEVICE_ENVIRONMENT,
                    BuildConfig.API_BASE_URL,
                    BuildConfig.DEVICE_ATTESTATION_MODE,
                    "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
                ),
                BuildConfig.APPLICATION_ID,
                BuildConfig.DEVICE_CLOUD_PROJECT_NUMBER,
                monitoring = (application as PheeeewApplication).monitoring,
            )
        val appVersionApi = AppVersionApi(apiDependencies.client.requests, "android")
        val appSettingsLauncher = AndroidAppSettingsLauncher(this@MainActivity)

        setContent {
            App(
                locationDependencies = locationDependencies,
                lastRecordedGroupRepository = lastRecordedGroupRepository,
                groupCreateSessionStore = groupCreateSessionStore,
                hasCompletedOnboarding = hasCompletedOnboarding,
                onOnboardingCompleted = {
                    onboardingPreferences.edit().putBoolean(WELCOME_ONBOARDING_COMPLETED_KEY, true).apply()
                },
                appVersion = BuildConfig.VERSION_NAME,
                appVersionApi = appVersionApi,
                permissionSettingsLauncher = appSettingsLauncher,
                appSettingsLauncher = appSettingsLauncher,
                apiDependencies = apiDependencies,
            )
        }
    }
}
