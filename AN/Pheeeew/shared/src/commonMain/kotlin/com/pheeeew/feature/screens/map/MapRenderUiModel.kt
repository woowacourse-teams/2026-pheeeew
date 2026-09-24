package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.MapCameraState

data class MapRenderUiModel(
    val currentLocation: CurrentLocation?,
    val locationState: LocationState,
    val fallbackCameraState: MapCameraState = MapCameraState(37.5665, 126.9780, 11.0),
    val cameraCommand: MapCameraCommandUiModel? = null,
)
