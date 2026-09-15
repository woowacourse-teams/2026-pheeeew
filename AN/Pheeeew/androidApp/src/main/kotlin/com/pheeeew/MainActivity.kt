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
import com.pheeeew.di.LocationDependencies
import com.pheeeew.di.SighModule
import com.pheeeew.di.createAndroidLocationDependencies
import com.pheeeew.feature.map.guide.resolveFirstSighGuideCompleted

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
        val sighDependencies = SighModule.create(ApiConfig(baseUrl = BuildConfig.API_BASE_URL))
        val appPreferences = getSharedPreferences(APP_PREFERENCES_NAME, MODE_PRIVATE)
        val hasCompletedOnboarding = appPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        val storedGuideCompletion =
            if (appPreferences.contains(KEY_FIRST_SIGH_GUIDE_COMPLETED)) {
                appPreferences.getBoolean(KEY_FIRST_SIGH_GUIDE_COMPLETED, false)
            } else {
                null
            }
        val hasCompletedFirstSighGuide =
            resolveFirstSighGuideCompleted(hasCompletedOnboarding, storedGuideCompletion)
        if (storedGuideCompletion == null && hasCompletedOnboarding) {
            appPreferences.edit().putBoolean(KEY_FIRST_SIGH_GUIDE_COMPLETED, true).apply()
        }

        setContent {
            App(
                appVersion = BuildConfig.VERSION_NAME,
                hasCompletedOnboarding = hasCompletedOnboarding,
                hasCompletedFirstSighGuide = hasCompletedFirstSighGuide,
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
                mapPerformanceLogger = { event ->
                    if (BuildConfig.DEBUG) Log.d("Pheeeew.MapPerf", event)
                },
            )
        }
    }
}

private const val APP_PREFERENCES_NAME = "pheeeew_preferences"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_FIRST_SIGH_GUIDE_COMPLETED = "first_sigh_guide_completed_v1"

class LocationDependenciesHolder : ViewModel() {
    var dependencies: LocationDependencies? = null

    override fun onCleared() {
        (dependencies?.permissionController as? AutoCloseable)?.close()
    }
}
