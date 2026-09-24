package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.MapCameraState

data class MapUiModel(
    val locationState: LocationState = LocationState.Loading,
    val cameraState: MapCameraState? = null,
    val mapError: MapErrorUiModel? = null,
    val mapRevision: Int = 0,
    val cameraCommand: MapCameraCommandUiModel? = null,
    val isRequestingLocation: Boolean = false,
)
