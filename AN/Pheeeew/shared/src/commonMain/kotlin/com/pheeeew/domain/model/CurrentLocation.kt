package com.pheeeew.domain.model

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtMillis: Long,
) {
    override fun toString(): String = "CurrentLocation([redacted])"
}
