package com.pheeeew.feature.screens.map.renderer

import android.graphics.Color
import com.pheeeew.feature.screens.map.EmotionPinUiModel
import com.pheeeew.feature.screens.map.symbolImageKey
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal class AndroidRecordStampLayer {
    private var lastPin: EmotionPinUiModel? = null

    fun install(style: Style) {
        lastPin = null
        style.addSource(GeoJsonSource("record-stamp", FeatureCollection.fromFeatures(emptyList())))
        style.addLayer(
            CircleLayer("record-stamp-halo", "record-stamp").withProperties(
                circleRadius(34f),
                circleColor(Color.argb(77, 255, 243, 191)),
                circleStrokeWidth(2f),
                circleStrokeColor(Color.rgb(229, 190, 40)),
            ),
        )
        style.addLayer(
            SymbolLayer("record-stamp-icon", "record-stamp").withProperties(
                iconSize(62f / 40f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            ),
        )
    }

    fun update(
        style: Style,
        pin: EmotionPinUiModel?,
        scale: Float,
    ) {
        style.getLayer("record-stamp-icon")?.setProperties(iconSize(62f / 40f * scale))
        style.getLayer("record-stamp-halo")?.setProperties(circleRadius(34f * scale), circleStrokeWidth(2f * scale))
        val drawable = pin?.takeIf { style.getImage(it.symbolImageKey()) != null }
        if (drawable == lastPin) return
        style.getSourceAs<GeoJsonSource>("record-stamp")?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    drawable?.let {
                        Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
                    },
                ),
            ),
        )
        drawable?.let { style.getLayer("record-stamp-icon")?.setProperties(iconImage(it.symbolImageKey())) }
        lastPin = drawable
    }
}
