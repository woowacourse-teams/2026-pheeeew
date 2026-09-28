package com.pheeeew.feature.screens.map.renderer

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor

internal object AndroidMapAppearance {
    private val background = Color.parseColor("#F7F7F4")
    private val landUse = Color.parseColor("#F2F1EE")
    private val building = Color.parseColor("#ECEBE9")
    private val park = Color.parseColor("#CFE3CC")
    private val parkOutline = Color.parseColor("#C1D8C0")
    private val water = Color.parseColor("#B8D8E8")
    private val road = Color.WHITE
    private val majorRoad = Color.parseColor("#FAFBFA")
    private val roadOutline = Color.parseColor("#D9DEE2")

    fun apply(style: Style) {
        style.layers.forEach { layer ->
            val layerId = layer.id.lowercase()
            when (layer) {
                is BackgroundLayer -> {
                    if (layerId == "background") layer.setProperties(backgroundColor(background))
                }

                is FillLayer -> fillColorFor(layer.sourceLayer.orEmpty().lowercase(), layerId)?.let { color ->
                    layer.setProperties(fillColor(color))
                }

                is LineLayer -> lineColorFor(layer.sourceLayer.orEmpty().lowercase(), layerId)?.let { color ->
                    layer.setProperties(lineColor(color))
                }
            }
        }
    }

    private fun fillColorFor(
        sourceLayer: String,
        layerId: String,
    ): Int? =
        when {
            sourceLayer == "water" || layerId == "water" -> water
            sourceLayer == "building" || layerId.contains("building") -> building
            sourceLayer == "park" || layerId.contains("park") -> park
            sourceLayer == "landcover" && (layerId.contains("wood") || layerId.contains("grass")) -> park
            sourceLayer == "landuse" && layerId.contains("residential") -> landUse
            else -> null
        }

    private fun lineColorFor(
        sourceLayer: String,
        layerId: String,
    ): Int? =
        when {
            sourceLayer == "waterway" || layerId.contains("waterway") -> water
            sourceLayer == "park" || layerId.contains("park_outline") -> parkOutline
            sourceLayer == "transportation" && layerId.contains("casing") -> roadOutline
            sourceLayer == "transportation" && majorRoadKeywords.any(layerId::contains) -> majorRoad
            sourceLayer == "transportation" -> road
            else -> null
        }

    private val majorRoadKeywords = listOf("motorway", "trunk", "primary", "secondary", "tertiary")
}
