package com.pheeeew.feature.map

import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.feature.map.map.MapCameraCommand

data class MapUiState(
    val sighs: List<SighPin> = emptyList(),
    val location: MapLocationUiState = MapLocationUiState(),
    val viewport: MapViewportUiState = MapViewportUiState(),
    val sighRelease: SighReleaseState = SighReleaseState.Idle,
    val errors: MapErrorUiState = MapErrorUiState(),
)

data class MapLocationUiState(
    val state: LocationState = LocationState.Loading,
    val isRequesting: Boolean = false,
)

data class MapViewportUiState(
    val cameraCommand: MapCameraCommand? = null,
    val focusRequest: MapFocusRequest? = null,
)

data class MapErrorUiState(
    val refreshMessage: String? = null,
    val renderMessage: String? = null,
)

sealed interface SighReleaseState {
    data object Idle : SighReleaseState

    data object Submitting : SighReleaseState

    data class Error(
        val message: String,
        val canRetry: Boolean,
    ) : SighReleaseState
}
