package com.pheeeew

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.data.local.device.AndroidDeviceIdStorage
import com.pheeeew.data.local.device.InMemoryAccessTokenStore
import com.pheeeew.di.LocationDependencies
import com.pheeeew.di.SighModule
import com.pheeeew.di.createAndroidDeviceRegistrationDependencies
import com.pheeeew.di.createAndroidDeviceRegistrationWithPlayIntegrityDependencies
import com.pheeeew.di.createAndroidLocationDependencies

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

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
            )

        setContent {
            App(
                appVersion = BuildConfig.VERSION_NAME,
                hasCompletedOnboarding = appPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false),
                onOnboardingCompleted = {
                    appPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
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

class LocationDependenciesHolder : ViewModel() {
    var dependencies: LocationDependencies? = null

    override fun onCleared() {
        (dependencies?.permissionController as? AutoCloseable)?.close()
    }
}
