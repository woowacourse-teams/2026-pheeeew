package com.pheeeew.legacy.feature.map.map

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer

/** OpenFreeMap dark style 위에 앱의 한국어 라벨 밀도 규칙을 적용합니다. */
internal object AndroidMapStyle {
    private const val BASE_SOURCE_ID = "openmaptiles"
    private const val POI_SOURCE_LAYER = "poi"
    private const val BUILDING_SOURCE_LAYER = "building"
    private const val POI_LABEL_LAYER_ID = "pheeeew-important-poi-labels"
    private const val BUILDING_LABEL_LAYER_ID = "pheeeew-building-labels"

    private val localizedSourceLayers =
        setOf(
            "aerodrome_label",
            "mountain_peak",
            "park",
            "place",
            "transportation_name",
            "water_name",
        )
    private val explicitlyHiddenSymbolSourceLayers = setOf(POI_SOURCE_LAYER, "housenumber")

    fun apply(style: Style) {
        applyBackgroundColor(style)
        recolorBaseLayers(style)
        hideUnnecessarySymbolLayers(style)
        localizeBaseLabels(style)
        replacePoiLabels(style)
        addBuildingLabels(style)
    }

    private fun applyBackgroundColor(style: Style) {
        style.layers
            .filterIsInstance<BackgroundLayer>()
            .firstOrNull()
            ?.setProperties(backgroundColor(Color.parseColor(MapDarkStyle.MAP_BACKGROUND_HEX)))
    }

    private fun recolorBaseLayers(style: Style) {
        style.layers.forEach { layer ->
            when (layer) {
                is FillLayer -> {
                    val color = fillColorFor(layer.sourceLayer.orEmpty(), layer.id) ?: return@forEach
                    runCatching { layer.setProperties(fillColor(Color.parseColor(color))) }
                }

                is FillExtrusionLayer -> {
                    if (isBuildingLayer(layer.sourceLayer.orEmpty(), layer.id)) {
                        runCatching {
                            layer.setProperties(fillExtrusionColor(Color.parseColor(MapDarkStyle.BUILDING_HEX)))
                        }
                    }
                }

                is LineLayer -> {
                    val color = lineColorFor(layer.sourceLayer.orEmpty(), layer.id) ?: return@forEach
                    runCatching { layer.setProperties(lineColor(Color.parseColor(color))) }
                }
            }
        }
    }

    private fun hideUnnecessarySymbolLayers(style: Style) {
        style.layers
            .filterIsInstance<SymbolLayer>()
            .filter { layer -> layer.sourceLayer in explicitlyHiddenSymbolSourceLayers }
            .forEach { layer ->
                runCatching { layer.setProperties(visibility(Property.NONE)) }
            }
    }

    private fun localizeBaseLabels(style: Style) {
        style.layers
            .filterIsInstance<SymbolLayer>()
            .filter { layer -> layer.sourceLayer in localizedSourceLayers }
            .forEach { layer ->
                runCatching {
                    layer.setProperties(
                        textField(localizedNameExpression()),
                        textColor(Color.parseColor(MapDarkStyle.LABEL_HEX)),
                        textHaloColor(Color.parseColor(MapDarkStyle.LABEL_HALO_HEX)),
                        textHaloWidth(MapDarkStyle.LABEL_HALO_WIDTH),
                    )
                }
            }
    }

    private fun replacePoiLabels(style: Style) {
        if (style.getSource(BASE_SOURCE_ID) == null) return

        // The upstream dark style may contain low-zoom POI icons/labels. Hide only
        // those layers and replace them with one deliberately sparse text layer.
        val existingPoiLayers =
            style.layers
                .filterIsInstance<SymbolLayer>()
                .filter { layer -> layer.sourceLayer == POI_SOURCE_LAYER }

        existingPoiLayers.forEach { layer ->
            runCatching { layer.setProperties(visibility(Property.NONE)) }
        }

        if (style.getLayer(POI_LABEL_LAYER_ID) != null) return

        val labelLayer =
            SymbolLayer(POI_LABEL_LAYER_ID, BASE_SOURCE_ID)
                .withSourceLayer(POI_SOURCE_LAYER)
                .withFilter(
                    Expression.all(
                        Expression.any(
                            Expression.has("name:ko"),
                            Expression.has("name"),
                            Expression.has("name_en"),
                            Expression.has("name:en"),
                        ),
                        Expression.lt(
                            Expression.get("rank"),
                            Expression.literal(MapDarkStyle.IMPORTANT_POI_MAX_RANK),
                        ),
                    ),
                ).withProperties(
                    textField(localizedNameExpression()),
                    textFont(arrayOf("Noto Sans Regular")),
                    textSize(MapDarkStyle.POI_LABEL_SIZE),
                    textMaxWidth(9f),
                    textColor(Color.parseColor(MapDarkStyle.LABEL_HEX)),
                    textHaloColor(Color.parseColor(MapDarkStyle.LABEL_HALO_HEX)),
                    textHaloWidth(MapDarkStyle.LABEL_HALO_WIDTH),
                    textAllowOverlap(false),
                    textIgnorePlacement(false),
                    iconAllowOverlap(false),
                    iconIgnorePlacement(false),
                )
        labelLayer.minZoom = MapDarkStyle.IMPORTANT_POI_MIN_ZOOM.toFloat()
        runCatching { style.addLayer(labelLayer) }
    }

    private fun addBuildingLabels(style: Style) {
        if (style.getSource(BASE_SOURCE_ID) == null || style.getLayer(BUILDING_LABEL_LAYER_ID) != null) return

        val labelLayer =
            SymbolLayer(BUILDING_LABEL_LAYER_ID, BASE_SOURCE_ID)
                .withSourceLayer(BUILDING_SOURCE_LAYER)
                .withFilter(hasLocalizedName())
                .withProperties(
                    textField(localizedNameExpression()),
                    textFont(arrayOf("Noto Sans Regular")),
                    textSize(MapDarkStyle.BUILDING_LABEL_SIZE),
                    textMaxWidth(8f),
                    textColor(Color.parseColor(MapDarkStyle.LABEL_HEX)),
                    textHaloColor(Color.parseColor(MapDarkStyle.LABEL_HALO_HEX)),
                    textHaloWidth(MapDarkStyle.LABEL_HALO_WIDTH),
                    textAllowOverlap(false),
                    textIgnorePlacement(false),
                    iconAllowOverlap(false),
                    iconIgnorePlacement(false),
                )
        labelLayer.minZoom = MapDarkStyle.BUILDING_LABEL_MIN_ZOOM.toFloat()
        runCatching { style.addLayer(labelLayer) }
    }

    private fun fillColorFor(
        sourceLayer: String,
        layerId: String,
    ): String? {
        val source = sourceLayer.lowercase()
        val id = layerId.lowercase()
        return when {
            isBuildingLayer(source, id) -> MapDarkStyle.BUILDING_HEX
            isWaterLayer(source, id) -> MapDarkStyle.WATER_HEX
            isParkLayer(source, id) -> MapDarkStyle.PARK_HEX
            source in LAND_SOURCE_LAYERS || id.contains("land") -> MapDarkStyle.LAND_HEX
            else -> null
        }
    }

    private fun lineColorFor(
        sourceLayer: String,
        layerId: String,
    ): String? {
        val source = sourceLayer.lowercase()
        val id = layerId.lowercase()
        return when {
            isWaterLayer(source, id) -> {
                MapDarkStyle.WATER_HEX
            }

            source == "transportation" || ROAD_ID_KEYWORDS.any(id::contains) -> {
                if (MAJOR_ROAD_ID_KEYWORDS.any(id::contains)) {
                    MapDarkStyle.MAJOR_ROAD_HEX
                } else {
                    MapDarkStyle.ROAD_HEX
                }
            }

            else -> {
                null
            }
        }
    }

    private fun isBuildingLayer(
        sourceLayer: String,
        layerId: String,
    ): Boolean = sourceLayer.lowercase() == BUILDING_SOURCE_LAYER || layerId.lowercase().contains("building")

    private fun isWaterLayer(
        sourceLayer: String,
        layerId: String,
    ): Boolean = sourceLayer.lowercase() in WATER_SOURCE_LAYERS || layerId.lowercase().contains("water")

    private fun isParkLayer(
        sourceLayer: String,
        layerId: String,
    ): Boolean {
        val source = sourceLayer.lowercase()
        val id = layerId.lowercase()
        return source in PARK_SOURCE_LAYERS && PARK_ID_KEYWORDS.any(id::contains)
    }

    private fun hasLocalizedName(): Expression =
        Expression.any(
            Expression.has("name:ko"),
            Expression.has("name"),
            Expression.has("name_en"),
            Expression.has("name:en"),
        )

    private fun localizedNameExpression(): Expression =
        Expression.coalesce(
            Expression.get("name:ko"),
            Expression.get("name"),
            Expression.get("name_en"),
            Expression.get("name:en"),
        )

    private val LAND_SOURCE_LAYERS = setOf("land", "landcover", "landuse")
    private val PARK_SOURCE_LAYERS = setOf("landcover", "landuse", "park")
    private val WATER_SOURCE_LAYERS = setOf("water", "water_name", "waterway")
    private val ROAD_ID_KEYWORDS = setOf("road", "street", "highway", "transportation")
    private val MAJOR_ROAD_ID_KEYWORDS = setOf("motorway", "trunk", "primary", "secondary", "major")
    private val PARK_ID_KEYWORDS =
        setOf("park", "wood", "forest", "grass", "garden", "recreation", "cemetery", "nature")
}
