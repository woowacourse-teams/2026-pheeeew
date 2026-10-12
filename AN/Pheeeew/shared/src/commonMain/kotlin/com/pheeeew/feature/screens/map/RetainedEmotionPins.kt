package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** The limit only evicts surrounding pins; every pin inside the viewport is retained. */
internal fun retainEmotionPins(
    pins: List<EmotionPinUiModel>,
    bounds: EmotionMapBounds,
    maximumPins: Int = MAXIMUM_RETAINED_EMOTION_PINS,
): List<EmotionPinUiModel> {
    require(maximumPins > 0)
    if (pins.size <= maximumPins) return pins

    val visibleIds =
        pins
            .filter {
                bounds.containsCoordinate(
                    it.latitude,
                    it.longitude,
                )
            }.mapTo(mutableSetOf()) { it.id }
    val remaining = (maximumPins - visibleIds.size).coerceAtLeast(0)
    if (remaining > 0) {
        pins
            .asSequence()
            .filterNot { it.id in visibleIds }
            .sortedWith(compareBy<EmotionPinUiModel> { bounds.distanceSquaredTo(it) }.thenBy { it.id })
            .take(remaining)
            .forEach { visibleIds.add(it.id) }
    }
    // Preserve drawing order and avoid source changes when membership has not changed.
    return pins.filter { it.id in visibleIds }
}

private fun EmotionMapBounds.containsCoordinate(
    latitude: Double,
    longitude: Double,
): Boolean =
    latitude in minLatitude..maxLatitude &&
        if (minLongitude <= maxLongitude) {
            longitude in minLongitude..maxLongitude
        } else {
            longitude >= minLongitude || longitude <= maxLongitude
        }

private fun EmotionMapBounds.distanceSquaredTo(pin: EmotionPinUiModel): Double {
    val latitudeDistance = max(0.0, max(minLatitude - pin.latitude, pin.latitude - maxLatitude))
    val longitudeInside =
        if (minLongitude <= maxLongitude) {
            pin.longitude in minLongitude..maxLongitude
        } else {
            pin.longitude >= minLongitude || pin.longitude <= maxLongitude
        }

    fun longitudeDistance(edge: Double): Double {
        val difference = abs(pin.longitude - edge)
        return min(difference, 360.0 - difference)
    }
    val longitudeDistance =
        if (longitudeInside) 0.0 else min(longitudeDistance(minLongitude), longitudeDistance(maxLongitude))
    val scaledLongitudeDistance = longitudeDistance * cos((minLatitude + maxLatitude) / 2.0 * kotlin.math.PI / 180.0)
    return latitudeDistance * latitudeDistance + scaledLongitudeDistance * scaledLongitudeDistance
}

internal const val MAXIMUM_RETAINED_EMOTION_PINS = 1_000
