package com.pheeeew

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.navigation.PredictiveBackContent
import com.pheeeew.developertools.DeveloperToolsInfo
import com.pheeeew.developertools.DeveloperToolsScreen

@Composable
internal fun DeveloperToolsEntry(
    build: DeviceSessionBuildConfig,
    content: @Composable ((() -> Unit)?) -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Box {
        content { visible = true }
        if (visible) {
            PredictiveBackContent(onBack = { visible = false }, content = {
                DeveloperToolsScreen(
                    info =
                        DeveloperToolsInfo(
                            platform = "Android",
                            version = BuildConfig.VERSION_NAME,
                            buildNumber = BuildConfig.VERSION_CODE.toString(),
                            buildConfiguration = BuildConfig.BUILD_TYPE,
                            serverEnvironment = build.environment,
                            apiBaseUrl = build.baseUrl,
                        ),
                    onBack = { visible = false },
                )
            })
        }
    }
}
