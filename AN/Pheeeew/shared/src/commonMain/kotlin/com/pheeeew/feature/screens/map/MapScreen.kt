package com.pheeeew.feature.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pheeeew.domain.model.LocationState
import com.pheeeew.feature.screens.map.renderer.NativeMap

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onNearbyListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.start()
    }
    val uiModel by viewModel.uiModel.collectAsState()
    val currentLocation = (uiModel.locationState as? LocationState.Available)?.location
    val renderModel = MapRenderUiModel(
        currentLocation = currentLocation,
        locationState = uiModel.locationState,
        cameraCommand = uiModel.cameraCommand,
    )

    Box(modifier = modifier.fillMaxSize()) {
        key(uiModel.mapRevision) {
            NativeMap(
                state = renderModel,
                onCameraStateChanged = viewModel::onCameraChanged,
                onMapError = viewModel::onMapError,
                onMapRecovered = viewModel::onMapRecovered,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (uiModel.mapError != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("지도를 불러오지 못했어요", color = Color.White)
                    Button(
                        onClick = {
                            viewModel.retryMap()
                        },
                    ) { Text("다시 시도") }
                }
            }
        }

        MapOverlay(
            onListClick = onNearbyListClick,
            onSettingClick = onSettingsClick,
            onMyLocationClick = viewModel::onMyLocationClick,
            isRequestingLocation = uiModel.isRequestingLocation,
        )
    }
}
