package com.pheeeew.data.remote.sigh.dto

import kotlinx.serialization.Serializable

/** GeoJSON Point. [coordinates]는 경도, 위도 순서입니다. */
@Serializable
data class PointGeometryDto(
    val type: String,
    val coordinates: List<Double>,
)
