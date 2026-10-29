package com.pheeeew.feature.screens.press

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.feature.screens.press.measurement.pressRaceDrawMeasurement
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun PressRoute(
    onOpenRanking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PressViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val faceDemo = rememberPressFaceDemoState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }
    PressScreen(
        uiState = uiState,
        onEmotionTap = { kind ->
            val accepted = viewModel.onEmotionTap(kind)
            faceDemo.onInput(kind, accepted)
            accepted
        },
        onOpenRanking = onOpenRanking,
        modifier = modifier.pressRaceDrawMeasurement(uiState.frameKey, viewModel::onFrameDrawn),
        nearbyRanking = uiState.nearbyRanking,
        activity = uiState.activity,
        faceVisuals = faceDemo.visuals,
    )
}
