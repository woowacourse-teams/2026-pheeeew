package com.pheeeew.feature.screens.press

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun PressRoute(
    onOpenRanking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PressViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
    PressScreen(
        uiState = uiState,
        onEmotionTap = viewModel::onEmotionTap,
        onOpenRanking = onOpenRanking,
        modifier = modifier,
    )
}
