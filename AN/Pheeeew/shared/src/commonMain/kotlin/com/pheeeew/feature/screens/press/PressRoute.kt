package com.pheeeew.feature.screens.press

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.domain.repository.press.PressRepository

@Composable
internal fun PressRoute(
    repository: PressRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: PressViewModel = viewModel { PressViewModel(repository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
    PressScreen(
        uiState = uiState,
        onEmotionTap = viewModel::onEmotionTap,
        modifier = modifier,
    )
}
