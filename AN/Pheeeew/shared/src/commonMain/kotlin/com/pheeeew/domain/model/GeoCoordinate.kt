package com.pheeeew.domain.model

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    override fun toString(): String = "GeoCoordinate([redacted])"
}
