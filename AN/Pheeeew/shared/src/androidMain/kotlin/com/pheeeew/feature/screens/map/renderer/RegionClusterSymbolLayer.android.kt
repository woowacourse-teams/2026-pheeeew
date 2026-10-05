package com.pheeeew.feature.screens.map.renderer

import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.gson.JsonObject
import com.pheeeew.feature.screens.map.EmotionPinSymbolImage
import com.pheeeew.feature.screens.map.RegionClusterUiModel
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal class RegionClusterSymbolLayer {
    private val registeredKeys = mutableSetOf<String>()
    private var lastRegions: List<RegionClusterUiModel>? = null

    fun install(style: Style) {
        registeredKeys.clear()
        lastRegions = null
        if (style.getSource(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        }
        if (style.getLayer(LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                    iconImage(Expression.get("image-key")),
                    iconSize(1f),
                    iconAnchor(Property.ICON_ANCHOR_CENTER),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
            )
        }
    }

    fun update(
        style: Style,
        regions: List<RegionClusterUiModel>,
        images: List<EmotionPinSymbolImage>,
    ) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        images.forEach { image ->
            if (image.key !in registeredKeys) {
                val bitmap = image.androidImageBitmap?.asAndroidBitmap() ?: return@forEach
                style.addImage(image.key, bitmap)
                registeredKeys.add(image.key)
            }
        }
        // Common presentation code owns replacement; the SDK adapter only checks registration.
        if (regions.any { it.symbolImageKey() !in registeredKeys }) return
        val drawable = regions
        if (drawable != lastRegions) {
            source.setGeoJson(
                FeatureCollection.fromFeatures(
                    drawable.map { region ->
                        Feature.fromGeometry(
                            Point.fromLngLat(region.longitude, region.latitude),
                            JsonObject().apply { addProperty("image-key", region.symbolImageKey()) },
                            region.id,
                        )
                    },
                ),
            )
            lastRegions = drawable
        }
        val desired = drawable.mapTo(mutableSetOf(), RegionClusterUiModel::symbolImageKey) + images.map { it.key }
        (registeredKeys - desired).forEach { key ->
            if (style.getImage(key) != null) style.removeImage(key)
            registeredKeys.remove(key)
        }
    }

    companion object {
        const val SOURCE_ID = "region-cluster-source"
        const val LAYER_ID = "region-cluster-layer"
    }
}
