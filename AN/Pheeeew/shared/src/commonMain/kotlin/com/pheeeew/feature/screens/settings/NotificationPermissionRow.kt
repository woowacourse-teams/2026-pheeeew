package com.pheeeew.feature.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.notification.allowsNotifications
import com.pheeeew.feature.screens.settings.components.SettingsActionRow
import com.pheeeew.feature.screens.settings.components.SettingsIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.notification_permission_cancel
import pheeeew.shared.generated.resources.notification_permission_denied_body
import pheeeew.shared.generated.resources.notification_permission_denied_title
import pheeeew.shared.generated.resources.notification_permission_open_failed
import pheeeew.shared.generated.resources.notification_permission_settings
import pheeeew.shared.generated.resources.notification_permission_title

@Composable
internal fun NotificationPermissionRow(
    controller: NotificationPermissionController,
    onOpen: () -> Unit,
) {
    val permissionState = remember(controller) { NotificationPermissionState(controller) }
    val state by permissionState.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var dialogVisible by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    SettingsActionRow(
        title = stringResource(Res.string.notification_permission_title),
        icon = SettingsIcon.Bell,
        highlighted = true,
        onClick =
            if (state.loading) {
                null
            } else {
                {
                    scope.launch {
                        permissionState.refresh()
                        if (!permissionState.state.value.authorization
                                .allowsNotifications() &&
                            permissionState.state.value.canRequestAuthorization
                        ) {
                            permissionState.requestPermission()
                        }
                        if (permissionState.state.value.authorization
                                .allowsNotifications()
                        ) {
                            onOpen()
                        } else {
                            dialogVisible = true
                        }
                    }
                }
            },
    )
    if (dialogVisible) {
        ConfirmDialog(
            title = stringResource(Res.string.notification_permission_denied_title),
            content =
                stringResource(
                    if (openFailed) {
                        Res.string.notification_permission_open_failed
                    } else {
                        Res.string.notification_permission_denied_body
                    },
                ),
            confirmText = stringResource(Res.string.notification_permission_settings),
            cancelText = stringResource(Res.string.notification_permission_cancel),
            onCancel = {
                dialogVisible = false
                openFailed = false
            },
            onConfirm = {
                scope.launch {
                    try {
                        if (permissionState.openSettings()) dialogVisible = false else openFailed = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        openFailed = true
                    }
                }
            },
        )
    }
}
