package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.GeoCoordinate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class IsWithinEmotionRecordRadiusUseCase {
    operator fun invoke(
        origin: GeoCoordinate,
        candidate: GeoCoordinate,
    ): Boolean = distanceMeters(origin, candidate) <= RECORD_SELECTION_RADIUS_METERS

    private fun distanceMeters(
        origin: GeoCoordinate,
        candidate: GeoCoordinate,
    ): Double {
        val originLatitudeRadians = origin.latitude.toRadians()
        val candidateLatitudeRadians = candidate.latitude.toRadians()
        val latitudeDifference = candidateLatitudeRadians - originLatitudeRadians
        val longitudeDifference = (candidate.longitude - origin.longitude).toRadians()
        val haversine =
            sin(latitudeDifference / 2).let { latitudeTerm ->
                latitudeTerm * latitudeTerm +
                    cos(originLatitudeRadians) * cos(candidateLatitudeRadians) *
                    sin(longitudeDifference / 2).let { longitudeTerm -> longitudeTerm * longitudeTerm }
            }
        val boundedHaversine = haversine.coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(boundedHaversine), sqrt(1 - boundedHaversine))
    }

    private fun Double.toRadians(): Double = this * PI / DEGREES_IN_HALF_CIRCLE

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val RECORD_SELECTION_RADIUS_METERS = 500.0
        const val DEGREES_IN_HALF_CIRCLE = 180.0
    }
}
