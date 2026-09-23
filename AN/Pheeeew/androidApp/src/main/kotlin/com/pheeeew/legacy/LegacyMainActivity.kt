package com.pheeeew.legacy

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pheeeew.BuildConfig
import com.pheeeew.legacy.core.network.ApiConfig
import com.pheeeew.legacy.data.local.device.AndroidDeviceIdStorage
import com.pheeeew.legacy.data.local.device.InMemoryAccessTokenStore
import com.pheeeew.legacy.data.remote.version.createAppVersionApi
import com.pheeeew.legacy.di.LocationDependencies
import com.pheeeew.legacy.di.SighModule
import com.pheeeew.legacy.di.createAndroidDeviceRegistrationDependencies
import com.pheeeew.legacy.di.createAndroidDeviceRegistrationWithPlayIntegrityDependencies
import com.pheeeew.legacy.di.createAndroidLocationDependencies
import com.pheeeew.legacy.feature.map.guide.resolveFirstSighGuideCompleted

class LegacyMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val firstSighGuidePreferences =
            resolveFirstSighGuidePreferences(getSharedPreferences(APP_PREFERENCES_NAME, MODE_PRIVATE))

        val holder = ViewModelProvider(this)[LocationDependenciesHolder::class.java]
        val locationDependencies =
            createAndroidLocationDependencies(
                activity = this,
                retainedDependencies = holder.dependencies,
            ).also { holder.dependencies = it }
        val appPreferences = getSharedPreferences(APP_PREFERENCES_NAME, MODE_PRIVATE)
        val accessTokenStore = InMemoryAccessTokenStore()
        val deviceDependencies =
            if (
                !BuildConfig.DEBUG && BuildConfig.DEVICE_CLOUD_PROJECT_NUMBER > 0L
            ) {
                createAndroidDeviceRegistrationWithPlayIntegrityDependencies(
                    context = this,
                    config = ApiConfig(baseUrl = BuildConfig.API_BASE_URL),
                    cloudProjectNumber = BuildConfig.DEVICE_CLOUD_PROJECT_NUMBER,
                    accessTokenStore = accessTokenStore,
                )
            } else {
                createAndroidDeviceRegistrationDependencies(
                    context = this,
                    config = ApiConfig(baseUrl = BuildConfig.API_BASE_URL),
                    accessTokenStore = accessTokenStore,
                )
            }
        val sighDependencies =
            SighModule.create(
                config = ApiConfig(baseUrl = BuildConfig.API_BASE_URL),
                deviceIdStorage = AndroidDeviceIdStorage(this),
                accessTokenStore = accessTokenStore,
                refreshAccessToken = {
                    deviceDependencies.ensureRegistered().getOrThrow().accessToken
                },
                monitoring = (application as PheeeewApplication).monitoring,
            )
        val appVersionApi = createAppVersionApi(ApiConfig(baseUrl = BuildConfig.API_BASE_URL), "android")
        val connectivityObserver = AndroidConnectivityObserver(this)

        setContent {
            LegacyApp(
                appVersion = BuildConfig.VERSION_NAME,
                monitoring = (application as PheeeewApplication).monitoring,
                appVersionApi = appVersionApi,
                connectivityObserver = connectivityObserver,
                hasCompletedOnboarding = firstSighGuidePreferences.hasCompletedOnboarding,
                hasCompletedFirstSighGuide = firstSighGuidePreferences.hasCompletedFirstSighGuide,
                onOnboardingCompleted = {
                    appPreferences
                        .edit()
                        .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                        .putBoolean(KEY_FIRST_SIGH_GUIDE_COMPLETED, false)
                        .apply()
                },
                onFirstSighGuideCompleted = {
                    appPreferences.edit().putBoolean(KEY_FIRST_SIGH_GUIDE_COMPLETED, true).apply()
                },
                locationDependencies = locationDependencies,
                sighRepository = sighDependencies.repository,
                createSigh = sighDependencies.createSigh,
                blockUser = sighDependencies.blockUser,
                reportSigh = sighDependencies.reportSigh,
                mapPerformanceLogger = { event ->
                    if (BuildConfig.DEBUG) Log.d("Pheeeew.MapPerf", event)
                },
                ensureDeviceRegistered = deviceDependencies.ensureRegistered,
            )
        }
    }
}

private const val APP_PREFERENCES_NAME = "pheeeew_preferences"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_FIRST_SIGH_GUIDE_COMPLETED = "first_sigh_guide_completed_v1"

private data class FirstSighGuidePreferences(
    val hasCompletedOnboarding: Boolean,
    val hasCompletedFirstSighGuide: Boolean,
)

private fun resolveFirstSighGuidePreferences(preferences: SharedPreferences): FirstSighGuidePreferences {
    val hasCompletedOnboarding = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    val storedGuideCompletion =
        if (preferences.contains(KEY_FIRST_SIGH_GUIDE_COMPLETED)) {
            preferences.getBoolean(KEY_FIRST_SIGH_GUIDE_COMPLETED, false)
        } else {
            null
        }
    val hasCompletedFirstSighGuide =
        com.pheeeew.legacy.feature.map.guide.resolveFirstSighGuideCompleted(
            hasCompletedOnboarding,
            storedGuideCompletion
        )
    if (storedGuideCompletion == null && hasCompletedOnboarding) {
        preferences.edit().putBoolean(KEY_FIRST_SIGH_GUIDE_COMPLETED, true).apply()
    }
    return FirstSighGuidePreferences(hasCompletedOnboarding, hasCompletedFirstSighGuide)
}

class LocationDependenciesHolder : ViewModel() {
    var dependencies: LocationDependencies? = null

    override fun onCleared() {
        (dependencies?.permissionController as? AutoCloseable)?.close()
    }
}
