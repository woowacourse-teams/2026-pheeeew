package com.pheeeew

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.iosDeviceBuildConfig
import com.pheeeew.core.di.iosOnboardingStore
import com.pheeeew.developertools.DeveloperToolsHost
import com.pheeeew.developertools.DeveloperToolsInfo
import com.pheeeew.developertools.DeveloperToolsSettingsEntry
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

@Suppress("ktlint:standard:function-naming")
fun DebugMainViewController(): UIViewController {
    initializeDeveloperTools()
    return ComposeUIViewController {
        val build = remember { iosDeviceBuildConfig() }
        val info =
            remember {
                DeveloperToolsInfo(
                    platform = "iOS",
                    version = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-",
                    buildNumber = NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String ?: "-",
                    buildConfiguration = if (build.isDebug) "Debug" else "Release",
                    serverEnvironment = build.environment,
                    apiBaseUrl = build.baseUrl,
                )
            }
        DeveloperToolsHost(
            info = info,
            onClearUiSession = {},
            onResetOnboarding = iosOnboardingStore()::resetCompletion,
        ) { onOpen ->
            MainApp(settingsContent = { DeveloperToolsSettingsEntry(onOpen) })
        }
    }
}
