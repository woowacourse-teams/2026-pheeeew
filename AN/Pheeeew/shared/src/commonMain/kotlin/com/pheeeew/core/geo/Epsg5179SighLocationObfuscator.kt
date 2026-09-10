package com.pheeeew.core.geo

import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.service.SighLocationObfuscator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class Epsg5179SighLocationObfuscator(
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
        val angleSample = nextUnitDouble()
        val radius = sqrt(radiusSample) * radiusMeters
        val angle = angleSample * TWO_PI
        val projected = Epsg5179Projection.forward(coordinate)
        val obfuscated =
            ProjectedCoordinate(
                easting = projected.easting + radius * cos(angle),
                northing = projected.northing + radius * sin(angle),
            )

        return Epsg5179Projection.inverse(obfuscated)
    }

    private fun nextUnitDouble(): Double =
        nextDouble().also { value ->
            require(value >= 0.0 && value < 1.0) {
                "난수는 0 이상 1 미만이어야 합니다."
            }
        }

    private companion object {
        const val TWO_PI = 2.0 * PI
    }
}
