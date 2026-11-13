package com.pheeeew.feature.screens.map.renderer

import android.graphics.Color
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.feature.screens.map.record.location.recordRangeGeoJson
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

internal class AndroidRecordRangeLayer {
    private var lastOrigin: GeoCoordinate? = null

    fun install(style: Style) {
        lastOrigin = null
        style.addSource(GeoJsonSource(SOURCE_ID, recordRangeGeoJson(null)))
        style.addLayer(
            FillLayer("record-range-dim", SOURCE_ID)
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("dim")))
                .withProperties(fillColor(Color.BLACK), fillOpacity(0.45f)),
        )
        style.addLayer(
            FillLayer("record-range-fill", SOURCE_ID)
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("range")))
                .withProperties(fillColor(RANGE_BLUE), fillOpacity(0.14f)),
        )
        style.addLayer(
            LineLayer("record-range-border", SOURCE_ID)
                .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("range")))
                .withProperties(lineColor(RANGE_BLUE), lineWidth(1.5f), lineDasharray(arrayOf(7f / 1.5f, 4f))),
        )
    }

    fun update(
        style: Style?,
        origin: GeoCoordinate?,
    ) {
        val source = style?.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        if (origin == lastOrigin) return
        source.setGeoJson(recordRangeGeoJson(origin))
        lastOrigin = origin
    }

    private companion object {
        const val SOURCE_ID = "record-range-source"
        val RANGE_BLUE = Color.rgb(57, 140, 255)
    }
}
