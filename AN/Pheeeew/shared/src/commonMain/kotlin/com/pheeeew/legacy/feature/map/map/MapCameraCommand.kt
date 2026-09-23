package com.pheeeew.legacy.feature.map.map

import com.pheeeew.legacy.domain.model.sigh.SighBounds

sealed interface MapCameraCommand {
    val id: Long

    data class ZoomBy(
        override val id: Long,
        val delta: Double,
    ) : MapCameraCommand

    data class MoveToCurrentLocation(
        override val id: Long,
        val zoom: Double?,
    ) : MapCameraCommand

    data class MoveToCoordinate(
        override val id: Long,
        val latitude: Double,
        val longitude: Double,
        val zoom: Double?,
        val verticalPosition: Double? = null,
    ) : MapCameraCommand

    data class MoveToBounds(
        override val id: Long,
        val bounds: SighBounds,
    ) : MapCameraCommand

    data class MoveToCameraState(
        override val id: Long,
        val camera: MapCameraState,
    ) : MapCameraCommand
}
