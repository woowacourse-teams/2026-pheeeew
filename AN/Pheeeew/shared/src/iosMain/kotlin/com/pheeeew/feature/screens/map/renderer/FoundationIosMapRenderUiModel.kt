package com.pheeeew.feature.screens.map.renderer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

data class FoundationIosMapRenderUiModel(
    val isVisible: Boolean,
    val isActive: Boolean,
    val restoredCamera: MapCameraSnapshotUiModel?,
    val monitoringLoadId: String?,
    val currentLocation: FoundationIosCurrentLocationUiModel?,
    val fallbackCenter: FoundationIosMapCoordinateUiModel,
    val cameraCommandId: Long,
    val cameraCommandType: Int,
    val cameraLatitude: Double,
    val cameraLongitude: Double,
    val cameraCommandValue: Double,
    val cameraVerticalPosition: Double,
    val isRecordLocationPicking: Boolean,
    val highlightedEmotionId: Long?,
    val pressedEmotionId: Long?,
    val pressedEmotionScale: Float,
    val focusedEmotionId: Long?,
    val recordPreviewScale: Float,
    val recordPreviewPin: FoundationIosEmotionPinCoordinateUiModel?,
    val recordOrigin: FoundationIosMapCoordinateUiModel?,
    val emotionPinCoordinates: List<FoundationIosEmotionPinCoordinateUiModel>,
    val regionClusterCoordinates: List<FoundationIosRegionClusterCoordinateUiModel>,
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
) {
    /** Copy while pinned: the returned data owns its bytes after Kotlin unpins the array. */
    @OptIn(ExperimentalForeignApi::class)
    fun copyRgbaData(): NSData =
        if (rgba.isEmpty()) {
            NSData()
        } else {
            rgba.usePinned { NSData.create(bytes = it.addressOf(0), length = rgba.size.toULong()) }
        }
}

data class FoundationIosRegionClusterCoordinateUiModel(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val imageKey: String,
)
