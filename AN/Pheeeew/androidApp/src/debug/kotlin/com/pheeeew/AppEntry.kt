package com.pheeeew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.notification.NotificationClickInbox
import com.pheeeew.core.notification.NotificationMessageInbox
import com.pheeeew.developertools.DeveloperToolsHost
import com.pheeeew.developertools.DeveloperToolsInfo
import com.pheeeew.developertools.DeveloperToolsSettingsEntry

@Composable
internal fun AppEntry(
    activity: ComponentActivity,
    appGraph: AppGraph,
    owner: ActivityRetainedGraphOwner,
    notificationClicks: NotificationClickInbox,
    notificationMessages: NotificationMessageInbox,
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
        configRepository = appGraph.apiDependencies.appConfig,
        onClearUiSession = owner::clearUiSession,
        onResetOnboarding = appGraph.onboardingStore::resetCompletion,
    ) { onOpen ->
        AndroidAppContent(
            activity,
            appGraph,
            owner,
            notificationClicks,
            notificationMessages,
        ) { DeveloperToolsSettingsEntry(onOpen) }
    }
}
