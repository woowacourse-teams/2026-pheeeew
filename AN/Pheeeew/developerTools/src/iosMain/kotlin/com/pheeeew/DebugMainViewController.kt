package com.pheeeew

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.IosApiDependencies
import com.pheeeew.core.navigation.PredictiveBackContent
import com.pheeeew.developertools.DeveloperToolsInfo
import com.pheeeew.developertools.DeveloperToolsScreen
import platform.Foundation.NSBundle

@Suppress("ktlint:standard:function-naming")
fun DebugMainViewController() =
    ComposeUIViewController {
        val build = remember { IosApiDependencies.instance.buildConfig }
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
        var visible by rememberSaveable { mutableStateOf(false) }
        Box {
            MainApp(onOpenDeveloperTools = { visible = true })
            if (visible) {
                PredictiveBackContent(onBack = { visible = false }, content = {
                    DeveloperToolsScreen(info, onBack = { visible = false })
                })
            }
        }
    }
