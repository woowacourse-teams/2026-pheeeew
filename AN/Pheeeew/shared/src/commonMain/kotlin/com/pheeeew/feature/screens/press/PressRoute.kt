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
        // Temporary display fixture authorized for #925 until the ranking API is connected.
        // These counts are independent of actual presses; do not calculate ranks from local input.
        nearbyRanking =
            PressNearbyRankingUiState(
                rows =
                    listOf(
                        PressNearbyRankRow(rank = 9, nickname = "예민한 고양이", count = 12),
                        PressNearbyRankRow(rank = 10, nickname = "나", count = 10, isMe = true),
                        PressNearbyRankRow(rank = 11, nickname = "조용한 오리", count = 9),
                    ),
                progress = 0.22f,
            ),
    )
}
