package com.pheeeew.feature.screens.press

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PressRoute(
    viewModel: PressViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
    PressScreen(
        uiState = uiState,
        onEmotionTap = viewModel::onEmotionTap,
        onRetryStatistics = { viewModel.refreshStatistics(force = true) },
        onRetryLocation = viewModel::retryLocation,
        modifier = modifier,
    )
}
