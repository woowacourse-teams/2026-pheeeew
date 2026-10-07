package com.pheeeew.feature.screens.press

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.feature.screens.press.data.PressDataSource

@Composable
internal fun PressRoute(
    dataSource: PressDataSource,
    modifier: Modifier = Modifier,
) {
    val viewModel: PressViewModel = viewModel { PressViewModel(dataSource) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PressScreen(
        uiState = uiState,
        onPeriodSelected = viewModel::onPeriodSelected,
        onEmotionTap = viewModel::onEmotionTap,
        modifier = modifier,
    )
}
