package com.pheeeew.feature.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal data class RegionClusterRenderState(
    val regions: List<RegionClusterUiModel> = emptyList(),
    val images: List<EmotionPinSymbolImage> = emptyList(),
)

/** Both native maps receive the same prepared symbols, including images retained during replacement. */
internal fun resolveRegionClusterRenderState(
    regions: List<RegionClusterUiModel>,
    images: List<EmotionPinSymbolImage>,
    previous: RegionClusterRenderState,
): RegionClusterRenderState {
    val preparedImages = images.associateBy { it.key }
    val retainedImages = previous.images.associateBy { it.key }
    val drawable = resolveDrawableRegionClusters(regions, previous.regions, preparedImages.keys)
    return RegionClusterRenderState(
        regions = drawable,
        images = drawable.map { it.symbolImageKey() }.distinct().mapNotNull { preparedImages[it] ?: retainedImages[it] },
    )
}

internal fun resolveDrawableRegionClusters(
    regions: List<RegionClusterUiModel>,
    previous: List<RegionClusterUiModel>,
    registeredImageKeys: Set<String>,
): List<RegionClusterUiModel> {
    val previousById = previous.associateBy { it.id }
    return regions.mapNotNull { region ->
        if (region.symbolImageKey() in registeredImageKeys) region else previousById[region.id]
    }.sortedBy { it.id }
}

private class RegionClusterRenderCache {
    var previous = RegionClusterRenderState()
}

@Composable
internal fun rememberRegionClusterRenderState(
    regions: List<RegionClusterUiModel>,
    images: List<EmotionPinSymbolImage>,
): RegionClusterRenderState {
    val cache = remember { RegionClusterRenderCache() }
    return remember(regions, images) {
        resolveRegionClusterRenderState(regions, images, cache.previous).also { cache.previous = it }
    }
}
