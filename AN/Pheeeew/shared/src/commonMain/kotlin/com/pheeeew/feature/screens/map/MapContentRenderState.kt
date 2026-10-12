package com.pheeeew.feature.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.feature.screens.map.monitoring.ContentLoad

/** One complete displayed snapshot; API responses are candidates until their images are ready. */
internal data class MapContentRenderState(
    val pins: List<EmotionPinUiModel> = emptyList(),
    val regions: List<RegionClusterUiModel> = emptyList(),
    val regionLevel: EmotionRegionLevel? = null,
    val pinImages: List<EmotionPinSymbolImage> = emptyList(),
    val regionImages: List<EmotionPinSymbolImage> = emptyList(),
    val contentLoad: ContentLoad? = null,
)

internal fun resolveMapContentRenderState(
    pending: MapContentRenderState,
    displayed: MapContentRenderState,
    hiddenIds: Set<Long>,
): MapContentRenderState {
    val regionMode = pending.regionLevel != null
    val pins = if (regionMode) emptyList() else pending.pins.filterNot { it.id in hiddenIds }
    val regions = if (regionMode) pending.regions else emptyList()
    val pinImages = (displayed.pinImages + pending.pinImages).filter { it.hasVisiblePixels }.associateBy { it.key }
    val regionImages = (displayed.regionImages + pending.regionImages).filter { it.hasVisiblePixels }.associateBy { it.key }
    if (pins.any { it.symbolImageKey() !in pinImages } || regions.any { it.symbolImageKey() !in regionImages }) {
        // IDs can change entirely between SIDO and SIGUNGU: retain the whole outgoing snapshot.
        return displayed.copy(pins = displayed.pins.filterNot { it.id in hiddenIds })
    }
    return pending.copy(
        pins = pins,
        regions = regions.sortedBy { it.id },
        pinImages = pins.map(EmotionPinUiModel::symbolImageKey).distinct().mapNotNull(pinImages::get),
        regionImages = regions.map(RegionClusterUiModel::symbolImageKey).distinct().mapNotNull(regionImages::get),
        contentLoad = pending.contentLoad.takeUnless { regionMode },
    )
}

private class MapContentRenderCache {
    var displayed = MapContentRenderState()
}

@Composable
internal fun rememberMapContentRenderState(
    pending: MapContentRenderState,
    hiddenIds: Set<Long>,
): MapContentRenderState {
    val cache = remember { MapContentRenderCache() }
    return remember(pending, hiddenIds) {
        resolveMapContentRenderState(pending, cache.displayed, hiddenIds).also { cache.displayed = it }
    }
}
