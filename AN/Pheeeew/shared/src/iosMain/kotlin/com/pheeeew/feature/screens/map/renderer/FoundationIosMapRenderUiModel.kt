package com.pheeeew.feature.screens.map.renderer

data class FoundationIosMapRenderUiModel(
    val currentLocation: FoundationIosCurrentLocationUiModel?,
    val fallbackCenter: FoundationIosMapCoordinateUiModel,
    val cameraCommandId: Long,
    val cameraCommandType: Int,
    val cameraLatitude: Double,
    val cameraLongitude: Double,
    val cameraCommandValue: Double,
) {
    override fun toString(): String = "FoundationIosMapRenderUiModel([redacted])"
}
