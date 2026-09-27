package com.pheeeew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pheeeew.core.di.AndroidApiDependencies
import com.pheeeew.core.di.device.DeviceSessionBuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App(
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
