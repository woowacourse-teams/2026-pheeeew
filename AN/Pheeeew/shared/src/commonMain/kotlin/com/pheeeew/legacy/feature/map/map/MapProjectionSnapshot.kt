package com.pheeeew.legacy.feature.map.map

data class MapScreenPoint(
    val id: String,
    val xPx: Float,
    val yPx: Float,
)

data class MapProjectionSnapshot(
    val revision: Long,
    val points: Map<String, MapScreenPoint>,
    val cameraIdle: Boolean,
) {
    companion object {
        val Empty = MapProjectionSnapshot(0L, emptyMap(), cameraIdle = false)
    }
}
