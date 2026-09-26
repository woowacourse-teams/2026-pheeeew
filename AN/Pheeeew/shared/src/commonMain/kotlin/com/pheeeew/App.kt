package com.pheeeew

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.di.ApiDependencies
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapViewModel

@Composable
fun App(
    locationDependencies: LocationDependencies,
    apiDependencies: ApiDependencies,
) {
    LaunchedEffect(apiDependencies) { apiDependencies.prepareSession() }
    val mapViewModel: MapViewModel =
        viewModel {
            MapViewModel.create(locationDependencies)
        }
    MapScreen(
        viewModel = mapViewModel,
        onListClick = {},
        onSettingClick = {},
        modifier = Modifier.fillMaxSize(),
    )
}
