package com.pheeeew.feature.screens.map.record.location

import com.pheeeew.domain.model.GeoCoordinate
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val RECORD_RADIUS_METERS = 500.0
const val RECORD_CAMERA_LIMIT_METERS = 650.0
private const val EARTH_RADIUS_METERS = 6_371_000.0

data class RecordMapViewport(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
)

// Camera bounds are rectangular so the map SDK can stop movement before applying it.
data class RecordCameraBounds(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
) {
    fun contains(coordinate: GeoCoordinate): Boolean =
        coordinate.latitude in south..north &&
            if (west <= east) {
                coordinate.longitude in west..east
            } else {
                coordinate.longitude >= west || coordinate.longitude <= east
            }
}

fun recordCameraBounds(origin: GeoCoordinate): RecordCameraBounds =
    RecordCameraBounds(
        south = destination(origin, RECORD_CAMERA_LIMIT_METERS, PI).latitude,
        north = destination(origin, RECORD_CAMERA_LIMIT_METERS, 0.0).latitude,
        west = destination(origin, RECORD_CAMERA_LIMIT_METERS, -PI / 2).longitude,
        east = destination(origin, RECORD_CAMERA_LIMIT_METERS, PI / 2).longitude,
    )

fun destination(
    origin: GeoCoordinate,
    meters: Double,
    bearing: Double,
): GeoCoordinate {
    val latitude = origin.latitude * PI / 180
    val longitude = origin.longitude * PI / 180
    val angular = meters / EARTH_RADIUS_METERS
    val targetLatitude =
        asin((sin(latitude) * cos(angular) + cos(latitude) * sin(angular) * cos(bearing)).coerceIn(-1.0, 1.0))
    val targetLongitude =
        longitude +
            atan2(
                sin(bearing) * sin(angular) * cos(latitude),
                cos(angular) - sin(latitude) * sin(targetLatitude),
            )
    return GeoCoordinate(targetLatitude * 180 / PI, (targetLongitude * 180 / PI + 540) % 360 - 180)
}

fun distance(
    origin: GeoCoordinate,
    target: GeoCoordinate,
): Double {
    val lat1 = origin.latitude * PI / 180
    val lat2 = target.latitude * PI / 180
    val dlat = lat2 - lat1
    val dlon = (target.longitude - origin.longitude) * PI / 180
    val value = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
    return EARTH_RADIUS_METERS * 2 * asin(sqrt(value.coerceIn(0.0, 1.0)))
}

fun bearing(
    origin: GeoCoordinate,
    target: GeoCoordinate,
): Double {
    val lat1 = origin.latitude * PI / 180
    val lat2 = target.latitude * PI / 180
    val dlon = (target.longitude - origin.longitude) * PI / 180
    return atan2(sin(dlon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dlon))
}

fun constrainToRecordRadius(
    origin: GeoCoordinate,
    target: GeoCoordinate,
): GeoCoordinate = constrainToRadius(origin, target, RECORD_RADIUS_METERS)

fun constrainToRadius(
    origin: GeoCoordinate,
    target: GeoCoordinate,
    radiusMeters: Double,
): GeoCoordinate =
    if (distance(origin, target) <= radiusMeters) {
        target
    } else {
        destination(origin, radiusMeters - 0.001, bearing(origin, target))
    }
