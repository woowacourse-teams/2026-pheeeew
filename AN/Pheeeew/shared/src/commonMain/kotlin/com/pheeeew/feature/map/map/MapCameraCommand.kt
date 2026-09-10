package com.pheeeew.feature.map.map

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
}
