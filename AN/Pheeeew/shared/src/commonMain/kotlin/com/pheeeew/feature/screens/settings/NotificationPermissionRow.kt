package com.pheeeew.feature.screens.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.feature.screens.settings.components.SettingsActionRow
import com.pheeeew.feature.screens.settings.components.SettingsIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.notification_permission_allowed_body
import pheeeew.shared.generated.resources.notification_permission_authorized
import pheeeew.shared.generated.resources.notification_permission_cancel
import pheeeew.shared.generated.resources.notification_permission_denied
import pheeeew.shared.generated.resources.notification_permission_denied_body
import pheeeew.shared.generated.resources.notification_permission_denied_title
import pheeeew.shared.generated.resources.notification_permission_ephemeral
import pheeeew.shared.generated.resources.notification_permission_ephemeral_body
import pheeeew.shared.generated.resources.notification_permission_failed
import pheeeew.shared.generated.resources.notification_permission_loading
import pheeeew.shared.generated.resources.notification_permission_not_determined
import pheeeew.shared.generated.resources.notification_permission_open_failed
import pheeeew.shared.generated.resources.notification_permission_provisional
import pheeeew.shared.generated.resources.notification_permission_provisional_body
import pheeeew.shared.generated.resources.notification_permission_request
import pheeeew.shared.generated.resources.notification_permission_request_body
import pheeeew.shared.generated.resources.notification_permission_settings
import pheeeew.shared.generated.resources.notification_permission_title
import pheeeew.shared.generated.resources.notification_permission_unknown
import pheeeew.shared.generated.resources.notification_permission_unknown_body

@Composable
internal fun NotificationPermissionRow(controller: NotificationPermissionController) {
    val permissionState = remember(controller) { NotificationPermissionState(controller) }
    val state by permissionState.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var dialogVisible by remember { mutableStateOf(false) }
    var settingsOpenFailed by remember { mutableStateOf(false) }
    val authorization = state.authorization

    // Runs on first entry and whenever the app returns from iOS Settings.
    LaunchedEffect(permissionState, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            permissionState.refresh()
        }
    }

    SettingsActionRow(
        title = stringResource(Res.string.notification_permission_title),
        icon = SettingsIcon.Tune,
        trailingText =
            stringResource(
                when {
                    state.loading -> Res.string.notification_permission_loading
                    state.failed -> Res.string.notification_permission_failed
                    else -> authorization.label()
                },
            ),
        onClick =
            if (state.loading) {
                null
            } else {
                {
                    scope.launch {
                        permissionState.refresh()
                        dialogVisible = permissionState.state.value.authorization != null
                    }
                }
            },
    )
    if (settingsOpenFailed) {
        Text(
            stringResource(Res.string.notification_permission_open_failed),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
        )
    }
    if (dialogVisible && authorization != null && !state.loading) {
        val needsRequest = state.canRequestAuthorization
        ConfirmDialog(
            title =
                stringResource(
                    if (authorization == NotificationAuthorization.DENIED) {
                        Res.string.notification_permission_denied_title
                    } else {
                        Res.string.notification_permission_title
                    },
                ),
            content =
                stringResource(
                    if (authorization == NotificationAuthorization.DENIED && state.canRequestAuthorization) {
                        Res.string.notification_permission_request_body
                    } else {
                        authorization.description()
                    },
                ),
            confirmText =
                stringResource(
                    if (needsRequest) {
                        Res.string.notification_permission_request
                    } else {
                        Res.string.notification_permission_settings
                    },
                ),
            cancelText = stringResource(Res.string.notification_permission_cancel),
            onCancel = { dialogVisible = false },
            onConfirm = {
                dialogVisible = false
                settingsOpenFailed = false
                scope.launch {
                    if (needsRequest) {
                        permissionState.requestPermission()
                    } else {
                        try {
                            settingsOpenFailed = !permissionState.openSettings()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            settingsOpenFailed = true
                        }
                    }
                }
            },
        )
    }
}

private fun NotificationAuthorization?.label() =
    when (this) {
        NotificationAuthorization.NOT_DETERMINED -> Res.string.notification_permission_not_determined
        NotificationAuthorization.DENIED -> Res.string.notification_permission_denied
        NotificationAuthorization.AUTHORIZED -> Res.string.notification_permission_authorized
        NotificationAuthorization.PROVISIONAL -> Res.string.notification_permission_provisional
        NotificationAuthorization.EPHEMERAL -> Res.string.notification_permission_ephemeral
        NotificationAuthorization.UNKNOWN, null -> Res.string.notification_permission_unknown
    }

private fun NotificationAuthorization.description() =
    when (this) {
        NotificationAuthorization.NOT_DETERMINED -> Res.string.notification_permission_request_body
        NotificationAuthorization.DENIED -> Res.string.notification_permission_denied_body
        NotificationAuthorization.AUTHORIZED -> Res.string.notification_permission_allowed_body
        NotificationAuthorization.PROVISIONAL -> Res.string.notification_permission_provisional_body
        NotificationAuthorization.EPHEMERAL -> Res.string.notification_permission_ephemeral_body
        NotificationAuthorization.UNKNOWN -> Res.string.notification_permission_unknown_body
    }
