package com.pheeeew.domain.model.emotion

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.group.GroupStamp

enum class EmotionState {
    FRUSTRATED,
    IRRITATED,
    EXHAUSTED,
    DISCOURAGED,
    ANGRY,
}

data class EmotionMapPin(
    val id: Long,
    val longitude: Double,
    val latitude: Double,
    val createdAt: String,
    val state: EmotionState,
    val rotationDegrees: Double,
    val groupStamp: GroupStamp?,
)

data class EmotionMapBounds(
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
) {
    fun isValid(): Boolean =
        minLongitude.isFinite() && maxLongitude.isFinite() &&
            minLatitude.isFinite() && maxLatitude.isFinite() &&
            minLongitude in -180.0..180.0 && maxLongitude in -180.0..180.0 &&
            minLatitude in -90.0..90.0 && maxLatitude in -90.0..90.0 &&
            minLatitude < maxLatitude && minLongitude != maxLongitude
}

/** Smallest longitude interval containing all coordinates, including intervals crossing 180°. */
fun enclosingEmotionMapBounds(coordinates: List<GeoCoordinate>): EmotionMapBounds? {
    if (coordinates.isEmpty() ||
        coordinates.any {
            !it.latitude.isFinite() || it.latitude !in -90.0..90.0 ||
                !it.longitude.isFinite() || it.longitude !in -180.0..180.0
        }
    ) {
        return null
    }
    val longitudes = coordinates.map { ((it.longitude % 360.0) + 360.0) % 360.0 }.sorted()
    var largestGapIndex = longitudes.lastIndex
    var largestGap = longitudes.first() + 360.0 - longitudes.last()
    for (index in 0 until longitudes.lastIndex) {
        val gap = longitudes[index + 1] - longitudes[index]
        if (gap > largestGap) {
            largestGap = gap
            largestGapIndex = index
        }
    }
    val start = longitudes[(largestGapIndex + 1) % longitudes.size]
    val end = longitudes[largestGapIndex]
    val minLatitude = coordinates.minOf { it.latitude }
    val maxLatitude = coordinates.maxOf { it.latitude }
    if (360.0 - largestGap >= 330.0) {
        return EmotionMapBounds(-180.0, minLatitude, 180.0, maxLatitude).takeIf(EmotionMapBounds::isValid)
    }

    fun wrap(value: Double): Double = if (value >= 180.0) value - 360.0 else value
    return EmotionMapBounds(
        minLongitude = wrap(start),
        minLatitude = minLatitude,
        maxLongitude = wrap(end),
        maxLatitude = maxLatitude,
    ).takeIf(EmotionMapBounds::isValid)
}

data class EmotionMapPage(
    val pins: List<EmotionMapPin>,
    val hasNext: Boolean,
    val nextCursor: String?,
    val invalidItemCount: Int,
)

data class EmotionMapSnapshot(
    val pins: List<EmotionMapPin>,
)

sealed interface EmotionMapFailure {
    data object Unavailable : EmotionMapFailure

    data object InvalidResponse : EmotionMapFailure
}

sealed interface EmotionMapPageResult {
    data class Success(
        val page: EmotionMapPage,
        val fromCache: Boolean = false,
    ) : EmotionMapPageResult

    data class Failure(
        val reason: EmotionMapFailure,
    ) : EmotionMapPageResult
}
