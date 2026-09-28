package com.pheeeew.feature.screens.map.renderer

data class FoundationIosMapRenderUiModel(
    val currentLocation: FoundationIosCurrentLocationUiModel?,
    val fallbackCenter: FoundationIosMapCoordinateUiModel,
    val cameraCommandId: Long,
    val cameraCommandType: Int,
    val cameraLatitude: Double,
    val cameraLongitude: Double,
    val cameraCommandValue: Double,
    val isRecordLocationPicking: Boolean,
    val highlightedEmotionId: Long?,
    val recordOrigin: FoundationIosMapCoordinateUiModel?,
    val emotionPinCoordinates: List<FoundationIosEmotionPinCoordinateUiModel>,
    val emotionPinSymbolImages: List<FoundationIosMapSymbolImageUiModel>,
) {
    override fun toString(): String = "FoundationIosMapRenderUiModel([redacted])"
}

data class FoundationIosEmotionPinCoordinateUiModel(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val imageKey: String,
    val rotationDegrees: Double,
)

data class FoundationIosMapSymbolImageUiModel(
    val key: String,
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
)
