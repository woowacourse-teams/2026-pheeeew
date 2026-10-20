package com.pheeeew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.di.AppGraph
import com.pheeeew.developertools.DeveloperToolsHost
import com.pheeeew.developertools.DeveloperToolsInfo
import com.pheeeew.developertools.DeveloperToolsSettingsEntry

@Composable
internal fun AppEntry(
    activity: ComponentActivity,
    appGraph: AppGraph,
    owner: ActivityRetainedGraphOwner,
) {
    val build = appGraph.apiDependencies.buildConfig
    DeveloperToolsHost(
        info =
            DeveloperToolsInfo(
                platform = "Android",
                version = BuildConfig.VERSION_NAME,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                buildConfiguration = BuildConfig.BUILD_TYPE,
                serverEnvironment = build.environment,
                apiBaseUrl = build.baseUrl,
            ),
        onClearUiSession = owner::clearUiSession,
    ) { onOpen ->
        AndroidAppContent(activity, appGraph, owner) { DeveloperToolsSettingsEntry(onOpen) }
    }
}
