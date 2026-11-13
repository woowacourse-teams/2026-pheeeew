package com.pheeeew.domain.model.emotion

import kotlin.math.ceil
import kotlin.math.floor

/** Query a padded, stable area so nearby viewports can share an administrative summary. */
internal fun EmotionMapBounds.expandedForRegionQuery(level: EmotionRegionLevel): EmotionMapBounds {
    val grid =
        when (level) {
            EmotionRegionLevel.SIDO -> 1.0
            EmotionRegionLevel.SIGUNGU -> 0.125
        }
    val latitudePadding = (maxLatitude - minLatitude) * 0.25
    val longitudeSpan =
        if (minLongitude <=
            maxLongitude
        ) {
            maxLongitude - minLongitude
        } else {
            360.0 - minLongitude + maxLongitude
        }
    val west = floor((minLongitude - longitudeSpan * 0.25) / grid) * grid
    val east = ceil((minLongitude + longitudeSpan * 1.25) / grid) * grid
    val coversWorld = east - west >= 360.0
    return EmotionMapBounds(
        minLongitude = if (coversWorld) -180.0 else wrapRegionLongitude(west),
        minLatitude = (floor((minLatitude - latitudePadding) / grid) * grid).coerceAtLeast(-90.0),
        maxLongitude = if (coversWorld) 180.0 else wrapRegionLongitude(east),
        maxLatitude = (ceil((maxLatitude + latitudePadding) / grid) * grid).coerceAtMost(90.0),
    )
}

/** Includes viewports crossing the antimeridian without treating them as near-global areas. */
internal fun EmotionMapBounds.coversRegionViewport(viewport: EmotionMapBounds): Boolean =
    minLatitude <= viewport.minLatitude && maxLatitude >= viewport.maxLatitude &&
        viewport.regionLongitudeIntervals().all { requested ->
            regionLongitudeIntervals().any { cached ->
                cached.start <= requested.start && cached.endInclusive >= requested.endInclusive
            }
        }

private fun EmotionMapBounds.regionLongitudeIntervals(): List<ClosedFloatingPointRange<Double>> =
    if (minLongitude <= maxLongitude) {
        listOf(minLongitude..maxLongitude)
    } else {
        listOf(minLongitude..180.0, -180.0..maxLongitude)
    }

private fun wrapRegionLongitude(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
