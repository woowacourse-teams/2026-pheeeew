package com.pheeeew.feature.screens.map

enum class MapCameraActionUiModel {
    MoveToCoordinate,
    ZoomBy,
}

data class MapCameraCommandUiModel(
    val id: Long,
    val action: MapCameraActionUiModel,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val value: Double,
    // Target position as a fraction of the map height, measured from the top.
    val verticalPosition: Double = 0.5,
)
