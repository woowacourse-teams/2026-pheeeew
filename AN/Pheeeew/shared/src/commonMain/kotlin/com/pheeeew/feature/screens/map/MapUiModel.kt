package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.LocationState

data class MapUiModel(
    val hiddenEmotionIds: Set<Long> = emptySet(),
    val locationState: LocationState = LocationState.Loading,
    val mapError: MapErrorUiModel? = null,
    val mapRevision: Int = 0,
    val cameraCommand: MapCameraCommandUiModel? = null,
    val isRequestingLocation: Boolean = false,
    val isEmotionSelectorExpanded: Boolean = false,
    val isRecordLocationPicking: Boolean = false,
    val recordOrigin: GeoCoordinate? = null,
)
