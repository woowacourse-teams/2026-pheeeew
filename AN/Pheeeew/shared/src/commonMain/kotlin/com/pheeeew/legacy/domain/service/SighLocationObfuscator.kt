package com.pheeeew.legacy.domain.service

import com.pheeeew.legacy.domain.model.geo.Coordinate

fun interface SighLocationObfuscator {
    fun obfuscate(
        coordinate: Coordinate,
        radiusMeters: Double,
    ): Coordinate

    fun obfuscate(coordinate: Coordinate): Coordinate =
        obfuscate(
            coordinate = coordinate,
            radiusMeters = DEFAULT_RADIUS_METERS,
        )

    companion object {
        const val DEFAULT_RADIUS_METERS = 300.0
    }
}
