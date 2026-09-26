package com.pheeeew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.pheeeew.core.di.AndroidApiDependencies
import com.pheeeew.core.di.DeviceSessionBuildConfig
import com.pheeeew.data.location.platform.android.LocationDependenciesHolder
import com.pheeeew.data.location.platform.android.createAndroidLocationDependencies

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
        setContent {
            App(
                locationDependencies = locationDependencies,
                apiDependencies =
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
                    ),
            )
        }
    }
}
