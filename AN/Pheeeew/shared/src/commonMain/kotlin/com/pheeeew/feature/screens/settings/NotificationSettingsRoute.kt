package com.pheeeew.feature.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun NotificationSettingsRoute(
    model: NotificationSettingsState,
    scope: CoroutineScope,
    onBackClick: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh() }
    }
    NotificationSettingsScreen(
        state = state,
        onBackClick = onBackClick,
        onToggleAll = { enabled -> scope.launch { model.toggleAll(enabled) } },
        onToggle = { kind, enabled -> scope.launch { model.toggle(kind, enabled) } },
        onRetry = { scope.launch { model.refresh() } },
    )
}
