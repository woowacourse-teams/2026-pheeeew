package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapBounds

/** Preload a quarter of the viewport span beyond each edge. */
internal fun EmotionMapBounds.expandedForPinQuery(): EmotionMapBounds {
    val latitudePadding = (maxLatitude - minLatitude) * PIN_QUERY_PADDING_FRACTION
    val longitudeSpan =
        if (minLongitude <= maxLongitude) maxLongitude - minLongitude else 360.0 - minLongitude + maxLongitude
    val longitudePadding = longitudeSpan * PIN_QUERY_PADDING_FRACTION
    val coversWorld = longitudeSpan + longitudePadding * 2.0 >= 360.0
    return EmotionMapBounds(
        minLongitude = if (coversWorld) -180.0 else wrapLongitude(minLongitude - longitudePadding),
        minLatitude = (minLatitude - latitudePadding).coerceAtLeast(-90.0),
        maxLongitude = if (coversWorld) 180.0 else wrapLongitude(maxLongitude + longitudePadding),
        maxLatitude = (maxLatitude + latitudePadding).coerceAtMost(90.0),
    )
}

private fun wrapLongitude(longitude: Double): Double = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

internal const val PIN_QUERY_PADDING_FRACTION = 0.25
