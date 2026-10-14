package com.pheeeew.feature.screens.map.renderer

import android.graphics.Color
import com.pheeeew.domain.model.CurrentLocation
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circlePitchScale
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal object AndroidCurrentLocationLayer {
    private const val SOURCE_ID = "foundation-current-location-source"
    private const val BORDER_LAYER_ID = "foundation-current-location-border"
    private const val DOT_LAYER_ID = "foundation-current-location-dot"
    private const val LOCATION_BLUE = 0xFF2F80ED.toInt()
    private var lastLocation: CurrentLocation? = null
    private var hasRenderedLocation = false

    fun install(style: Style) {
        lastLocation = null
        hasRenderedLocation = false
        if (style.getSource(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        }
        if (style.getLayer(BORDER_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(BORDER_LAYER_ID, SOURCE_ID).withProperties(
                    circleRadius(9f),
                    circleColor(Color.WHITE),
                    circlePitchScale(Property.CIRCLE_PITCH_SCALE_MAP),
                ),
            )
        }
        if (style.getLayer(DOT_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(DOT_LAYER_ID, SOURCE_ID).withProperties(
                    circleRadius(6f),
                    circleColor(LOCATION_BLUE),
                    circlePitchScale(Property.CIRCLE_PITCH_SCALE_MAP),
                ),
            )
        }
    }

    fun update(
        style: Style?,
        location: CurrentLocation?,
    ) {
        val validLocation = location?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
        if (hasRenderedLocation && validLocation == lastLocation) return
        val features =
            validLocation
                ?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
                ?.let(::listOf)
                ?: emptyList()
        val source = style?.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        lastLocation = validLocation
        hasRenderedLocation = true
    }
}
