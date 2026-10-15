package com.pheeeew

import androidx.compose.runtime.Composable
import com.pheeeew.core.di.device.DeviceSessionBuildConfig

@Composable
internal fun DeveloperToolsEntry(
    build: DeviceSessionBuildConfig,
    content: @Composable ((() -> Unit)?) -> Unit,
) {
    content(null)
}
