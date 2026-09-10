package com.pheeeew.core.geo

import com.pheeeew.domain.model.geo.Coordinate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal data class ProjectedCoordinate(
    val easting: Double,
    val northing: Double,
)

internal object Epsg5179Projection {
    // EPSG:5179: Korea 2000 / Unified CS, GRS80 ellipsoid.
    private const val SEMI_MAJOR_AXIS = 6_378_137.0
    private const val INVERSE_FLATTENING = 298.257222101
    private const val CENTRAL_MERIDIAN_DEGREES = 127.0
    private const val LATITUDE_OF_ORIGIN_DEGREES = 38.0
    private const val SCALE_FACTOR = 0.9996
    private const val FALSE_EASTING = 1_000_000.0
    private const val FALSE_NORTHING = 2_000_000.0

    private const val DEGREES_TO_RADIANS = PI / 180.0
    private const val RADIANS_TO_DEGREES = 180.0 / PI

    private val flattening = 1.0 / INVERSE_FLATTENING
    private val eccentricitySquared = flattening * (2.0 - flattening)
    private val secondEccentricitySquared = eccentricitySquared / (1.0 - eccentricitySquared)
    private val centralMeridian = CENTRAL_MERIDIAN_DEGREES * DEGREES_TO_RADIANS
    private val latitudeOfOrigin = LATITUDE_OF_ORIGIN_DEGREES * DEGREES_TO_RADIANS

    fun forward(coordinate: Coordinate): ProjectedCoordinate {
        val latitude = coordinate.latitude * DEGREES_TO_RADIANS
        val longitude = coordinate.longitude * DEGREES_TO_RADIANS
        val deltaLongitude = longitude - centralMeridian
        val cosLatitude = cos(latitude)
        val tangentSquared = tan(latitude).pow(2)
        val c = secondEccentricitySquared * cosLatitude.pow(2)
        val n = radiusOfCurvature(latitude)
        val a = cosLatitude * deltaLongitude
        val meridionalArc = meridionalArc(latitude)
        val originMeridionalArc = meridionalArc(latitudeOfOrigin)
        val eastingSeries =
            a +
                (1.0 - tangentSquared + c) * a.pow(3) / 6.0 +
                (
                    5.0 - 18.0 * tangentSquared + tangentSquared.pow(2) + 72.0 * c -
                        58.0 * secondEccentricitySquared
                ) * a.pow(5) / 120.0
        val northingPolynomial =
            a.pow(2) / 2.0 +
                (5.0 - tangentSquared + 9.0 * c + 4.0 * c.pow(2)) * a.pow(4) / 24.0 +
                (
                    61.0 - 58.0 * tangentSquared + tangentSquared.pow(2) + 600.0 * c -
                        330.0 * secondEccentricitySquared
                ) * a.pow(6) / 720.0
        val northingSeries =
            meridionalArc - originMeridionalArc +
                n * tan(latitude) * northingPolynomial

        val easting = FALSE_EASTING + SCALE_FACTOR * n * eastingSeries
        val northing = FALSE_NORTHING + SCALE_FACTOR * northingSeries
        return ProjectedCoordinate(easting = easting, northing = northing)
    }

    fun inverse(coordinate: ProjectedCoordinate): Coordinate {
        val x = (coordinate.easting - FALSE_EASTING) / SCALE_FACTOR
        val y = (coordinate.northing - FALSE_NORTHING) / SCALE_FACTOR
        val originMeridionalArc = meridionalArc(latitudeOfOrigin)
        val footpointLatitude = footpointLatitude(originMeridionalArc + y)
        val cosFootpoint = cos(footpointLatitude)
        val tangentSquared = tan(footpointLatitude).pow(2)
        val c = secondEccentricitySquared * cosFootpoint.pow(2)
        val n = radiusOfCurvature(footpointLatitude)
        val r = meridionalRadiusOfCurvature(footpointLatitude)
        val d = x / n

        val latitudeTerm =
            (
                5.0 + 3.0 * tangentSquared + 10.0 * c - 4.0 * c.pow(2) -
                    9.0 * secondEccentricitySquared
            ) * d.pow(4) / 24.0
        val latitudeTerm2 =
            (
                61.0 + 90.0 * tangentSquared + 298.0 * c + 45.0 * tangentSquared.pow(2) -
                    252.0 * secondEccentricitySquared - 3.0 * c.pow(2)
            ) * d.pow(6) / 720.0
        val latitudeCorrection = d.pow(2) / 2.0 - latitudeTerm + latitudeTerm2
        val longitudeTerm =
            (1.0 + 2.0 * tangentSquared + c) * d.pow(3) / 6.0
        val longitudeTerm2 =
            (
                5.0 - 2.0 * c + 28.0 * tangentSquared - 3.0 * c.pow(2) +
                    8.0 * secondEccentricitySquared + 24.0 * tangentSquared.pow(2)
            ) * d.pow(5) / 120.0
        val longitudeCorrection = d - longitudeTerm + longitudeTerm2
        val latitude =
            footpointLatitude -
                (n * tan(footpointLatitude) / r) * latitudeCorrection
        val longitude = centralMeridian + longitudeCorrection / cosFootpoint

        return Coordinate(
            latitude = latitude * RADIANS_TO_DEGREES,
            longitude = longitude * RADIANS_TO_DEGREES,
        )
    }

    private fun radiusOfCurvature(latitude: Double): Double =
        SEMI_MAJOR_AXIS / sqrt(1.0 - eccentricitySquared * sin(latitude).pow(2))

    private fun meridionalRadiusOfCurvature(latitude: Double): Double =
        SEMI_MAJOR_AXIS * (1.0 - eccentricitySquared) /
            (1.0 - eccentricitySquared * sin(latitude).pow(2)).pow(1.5)

    private fun meridionalArc(latitude: Double): Double {
        val e2 = eccentricitySquared
        val e4 = e2.pow(2)
        val e6 = e2.pow(3)
        val firstTerm = (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * latitude
        val secondTerm =
            (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * sin(2.0 * latitude)
        val thirdTerm =
            (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * sin(4.0 * latitude)
        val fourthTerm = (35.0 * e6 / 3072.0) * sin(6.0 * latitude)
        return SEMI_MAJOR_AXIS * (firstTerm - secondTerm + thirdTerm - fourthTerm)
    }

    private fun footpointLatitude(meridionalArc: Double): Double {
        val squareRoot = sqrt(1.0 - eccentricitySquared)
        val e1 = (1.0 - squareRoot) / (1.0 + squareRoot)
        val meridionalArcCoefficient =
            1.0 - eccentricitySquared / 4.0 -
                3.0 * eccentricitySquared.pow(2) / 64.0 -
                5.0 * eccentricitySquared.pow(3) / 256.0
        val mu =
            meridionalArc / (SEMI_MAJOR_AXIS * meridionalArcCoefficient)
        return mu +
            (3.0 * e1 / 2.0 - 27.0 * e1.pow(3) / 32.0) * sin(2.0 * mu) +
            (21.0 * e1.pow(2) / 16.0 - 55.0 * e1.pow(4) / 32.0) * sin(4.0 * mu) +
            (151.0 * e1.pow(3) / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1.pow(4) / 512.0) * sin(8.0 * mu)
    }
}
