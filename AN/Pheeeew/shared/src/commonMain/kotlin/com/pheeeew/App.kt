package com.pheeeew

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel

@Composable
fun App(locationDependencies: LocationDependencies) {
    val mapViewModel: MapViewModel =
        viewModel {
            MapViewModel.create(locationDependencies)
        }
    val mapRecordViewModel: MapRecordViewModel =
        viewModel {
            MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase())
        }
    MapScreen(
        viewModel = mapViewModel,
        recordViewModel = mapRecordViewModel,
        onListClick = {},
        onSettingClick = {},
        onEmotionBubbleClick = {},
        modifier = Modifier.fillMaxSize(),
    )
}
