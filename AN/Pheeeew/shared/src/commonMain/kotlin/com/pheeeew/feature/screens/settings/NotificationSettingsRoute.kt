package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.notification.NotificationPreferencesStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.notification_settings_save_failed

@Composable
internal fun NotificationSettingsRoute(
    permissions: NotificationPermissionController,
    store: NotificationPreferencesStore,
    onBackClick: () -> Unit,
) {
    val model = remember(permissions, store) { NotificationSettingsState(permissions, store) }
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh() }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val failureMessage = stringResource(Res.string.notification_settings_save_failed)
    LaunchedEffect(model, lifecycleOwner, failureMessage) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.state.collect { current ->
                current.saveFailureNoticeId?.let { id ->
                    model.acknowledgeSaveFailure(id)
                    snackbarHostState.showSnackbar(failureMessage)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        NotificationSettingsScreen(
            state = state,
            onBackClick = onBackClick,
            onToggleAll = { enabled -> scope.launch { model.toggleAll(enabled) } },
            onToggle = { kind, enabled -> scope.launch { model.toggle(kind, enabled) } },
            onRetry = { scope.launch { model.refresh() } },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .padding(16.dp),
            snackbar = { data ->
                Snackbar(
                    message = data.visuals.message,
                    onDismiss = data::dismiss,
                    isError = true,
                    presentationKey = data,
                )
            },
        )
    }
}
