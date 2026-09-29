package com.pheeeew.feature.screens.map.renderer

data class FoundationIosCurrentLocationUiModel(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
) {
    override fun toString(): String = "FoundationIosCurrentLocationUiModel([redacted])"
}
