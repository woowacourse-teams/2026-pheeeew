package com.pheeeew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.di.AppGraph
import com.pheeeew.data.remote.api.NotificationSettingsApi
import com.pheeeew.domain.model.device.DeviceSessionResult

@Composable
fun AndroidAppContent(
    activity: ComponentActivity,
    appGraph: AppGraph,
    owner: ActivityRetainedGraphOwner,
    settingsContent: @Composable () -> Unit = {},
) {
    val uiGraph = remember(owner) { owner.attach(activity) }
    val notificationSettings =
        remember(appGraph) {
            NotificationSettingsApi(appGraph.apiDependencies.client.requests) {
                (appGraph.apiDependencies.session.state.value as? DeviceSessionResult.Ready)?.access?.generation
            }
        }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner.uiSession) {
        App(
            appGraph = appGraph,
            uiGraph = uiGraph,
            settingsContent = settingsContent,
            notificationPermissionController = owner.notificationPermissionController,
            notificationPreferencesStore = notificationSettings,
        )
    }
}
