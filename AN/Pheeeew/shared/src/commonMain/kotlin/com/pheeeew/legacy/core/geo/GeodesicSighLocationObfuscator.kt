package com.pheeeew.legacy.core.geo

import com.pheeeew.legacy.domain.model.geo.Coordinate
import com.pheeeew.legacy.domain.service.SighLocationObfuscator
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GeodesicSighLocationObfuscator(
    private val nextDouble: () -> Double = { Random.nextDouble() },
) : SighLocationObfuscator {
    override fun obfuscate(
        coordinate: Coordinate,
        radiusMeters: Double,
    ): Coordinate {
        require(coordinate.latitude.isFinite() && coordinate.longitude.isFinite()) {
            "좌표는 유한한 숫자여야 합니다."
        }
        require(coordinate.latitude in -90.0..90.0 && coordinate.longitude in -180.0..180.0) {
            "좌표 범위가 올바르지 않습니다."
        }
        require(radiusMeters.isFinite() && radiusMeters >= 0.0) {
            "반경은 0 이상의 유한한 숫자여야 합니다."
        }
        if (radiusMeters == 0.0) return coordinate

        val radiusSample = nextUnitDouble()
        val bearing = nextUnitDouble() * TWO_PI
        val maxAngularDistance = radiusMeters / EARTH_MEAN_RADIUS_METERS
        val angularDistance =
            acos(
                1.0 - radiusSample * (1.0 - cos(maxAngularDistance)),
            )
        val latitude = coordinate.latitude.toRadians()
        val longitude = coordinate.longitude.toRadians()
        val destinationLatitude =
            asin(
                (
                    sin(latitude) * cos(angularDistance) +
                        cos(latitude) * sin(angularDistance) * cos(bearing)
                ).coerceIn(-1.0, 1.0),
            )
        val destinationLongitude =
            longitude +
                atan2(
                    sin(bearing) * sin(angularDistance) * cos(latitude),
                    cos(angularDistance) - sin(latitude) * sin(destinationLatitude),
                )

        return Coordinate(
            latitude = destinationLatitude.toDegrees(),
            longitude = normalizeLongitude(destinationLongitude.toDegrees()),
        )
    }

    private fun nextUnitDouble(): Double =
        nextDouble().also { value ->
            require(value >= 0.0 && value < 1.0) {
                "난수는 0 이상 1 미만이어야 합니다."
            }
        }

    private fun normalizeLongitude(longitude: Double): Double = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun Double.toRadians(): Double = this * PI / 180.0

    private fun Double.toDegrees(): Double = this * 180.0 / PI

    private companion object {
        const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8
        const val TWO_PI = 2.0 * PI
    }
}
