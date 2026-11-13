package com.pheeeew.feature.screens.map

/** Missing keys are pending; null entries record completed rasterization failures. */
internal data class MapSymbolImages(
    val images: List<EmotionPinSymbolImage>,
    val failedKeys: Set<String>,
)

internal fun Map<String, EmotionPinSymbolImage?>.symbolImagesFor(keys: Set<String>): MapSymbolImages =
    MapSymbolImages(
        images = keys.mapNotNull { get(it) },
        failedKeys = keys.filterTo(mutableSetOf()) { containsKey(it) && get(it) == null },
    )
