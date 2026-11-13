package com.pheeeew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.pheeeew.core.di.AppUiSession
import com.pheeeew.core.di.IosGraphs
import com.pheeeew.core.notification.IosNotificationClicks
import com.pheeeew.core.notification.IosNotificationMessages
import com.pheeeew.core.notification.IosNotificationPermissionController
import com.pheeeew.data.remote.api.NotificationSettingsApi
import com.pheeeew.domain.model.device.DeviceSessionResult

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { MainApp() }

@Composable
fun MainApp(settingsContent: @Composable () -> Unit = {}) {
    val appGraph = IosGraphs.app
    val uiGraph = remember(appGraph) { appGraph.controllerFactory.create() }
    val session = remember(uiGraph) { AppUiSession() }
    val notificationPreferences =
        remember(appGraph) {
            NotificationSettingsApi(appGraph.apiDependencies.client.requests) {
                (appGraph.apiDependencies.session.state.value as? DeviceSessionResult.Ready)?.access?.generation
            }
        }
    val notificationPermissions =
        remember(appGraph) { IosNotificationPermissionController(appGraph.appSettingsLauncher) }
    DisposableEffect(uiGraph) {
        onDispose {
            session.close()
            uiGraph.locationDependencies.close()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
        App(
            appGraph,
            uiGraph,
            settingsContent,
            notificationClicks = IosNotificationClicks.inbox,
            notificationMessages = IosNotificationMessages.inbox,
            notificationPermissionController = notificationPermissions,
            notificationPreferencesStore = notificationPreferences,
        )
    }
}
