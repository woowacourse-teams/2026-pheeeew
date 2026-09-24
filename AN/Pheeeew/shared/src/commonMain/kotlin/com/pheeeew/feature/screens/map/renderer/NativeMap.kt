package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.domain.model.MapCameraState
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel

@Composable
internal expect fun NativeMap(
    state: MapUiModel,
    onCameraStateChanged: (MapCameraState) -> Unit,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    modifier: Modifier,
)
