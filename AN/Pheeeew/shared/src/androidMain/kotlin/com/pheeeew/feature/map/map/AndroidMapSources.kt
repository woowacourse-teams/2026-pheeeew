package com.pheeeew.feature.map.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.google.gson.JsonObject
import com.pheeeew.core.designsystem.DesignSystemColors
import com.pheeeew.domain.model.location.CurrentLocation
import com.pheeeew.feature.map.SighMarker
import com.pheeeew.feature.map.star.StarVisual
import com.pheeeew.feature.map.star.StarVisualPolicy
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circlePitchScale
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 앱 소유 GeoJSON source와 레이어를 설치하고 상태만 교체합니다. */
internal object AndroidMapSources {
    const val MARKER_ID_PROPERTY = "sigh-id"

    private const val FEATURE_KIND_PROPERTY = "location-kind"
    private const val FEATURE_KIND_POINT = "point"
    private const val STAR_IMAGE_PROPERTY = "star-image"
    private const val STAR_SCALE_PROPERTY = "star-scale"
    private const val STAR_OPACITY_PROPERTY = "star-opacity"
    private const val STAR_BITMAP_SIZE = 96

    fun sighLayerIds(): Array<String> = arrayOf(MapDarkStyle.SIGH_LAYER_ID)

    fun install(style: Style) {
        installSighLayers(style)
        installCurrentLocationLayers(style)
    }

    fun updateSighs(
        style: Style,
        markers: List<SighMarker>,
    ) {
        val features =
            markers.map { marker ->
                val properties =
                    JsonObject().apply {
                        addProperty(MARKER_ID_PROPERTY, marker.id)
                        addProperty(STAR_IMAGE_PROPERTY, marker.visual.imageKey)
                        addProperty(STAR_SCALE_PROPERTY, marker.visual.scale)
                        addProperty(STAR_OPACITY_PROPERTY, marker.visual.opacity)
                    }
                Feature.fromGeometry(
                    Point.fromLngLat(marker.longitude, marker.latitude),
                    properties,
                    marker.id,
                )
            }

        style
            .getSourceAs<GeoJsonSource>(MapDarkStyle.SIGH_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun updateCurrentLocation(
        style: Style,
        location: CurrentLocation?,
    ) {
        val features =
            if (location == null) {
                emptyList()
            } else {
                val pointProperties =
                    JsonObject().apply {
                        addProperty(FEATURE_KIND_PROPERTY, FEATURE_KIND_POINT)
                    }
                listOf(
                    Feature.fromGeometry(
                        Point.fromLngLat(location.longitude, location.latitude),
                        pointProperties,
                    ),
                )
            }

        style
            .getSourceAs<GeoJsonSource>(MapDarkStyle.CURRENT_LOCATION_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun installSighLayers(style: Style) {
        StarVisualPolicy.allVisuals.forEach { visual ->
            if (style.getImage(visual.imageKey) == null) {
                style.addImage(visual.imageKey, createSighStarBitmap(visual))
            }
        }
        if (style.getSource(MapDarkStyle.SIGH_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    MapDarkStyle.SIGH_SOURCE_ID,
                    FeatureCollection.fromFeatures(emptyList()),
                ),
            )
        }
        val layerId = MapDarkStyle.SIGH_LAYER_ID
        if (style.getLayer(layerId) == null) {
            style.addLayer(
                SymbolLayer(layerId, MapDarkStyle.SIGH_SOURCE_ID)
                    .withProperties(
                        iconImage(Expression.get(STAR_IMAGE_PROPERTY)),
                        iconSize(Expression.get(STAR_SCALE_PROPERTY)),
                        iconOpacity(Expression.get(STAR_OPACITY_PROPERTY)),
                        iconAnchor(Property.ICON_ANCHOR_CENTER),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
            )
        }
    }

    private fun installCurrentLocationLayers(style: Style) {
        if (style.getSource(MapDarkStyle.CURRENT_LOCATION_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    MapDarkStyle.CURRENT_LOCATION_SOURCE_ID,
                    FeatureCollection.fromFeatures(emptyList()),
                ),
            )
        }

        if (style.getLayer(MapDarkStyle.CURRENT_LOCATION_BORDER_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(
                    MapDarkStyle.CURRENT_LOCATION_BORDER_LAYER_ID,
                    MapDarkStyle.CURRENT_LOCATION_SOURCE_ID,
                ).withFilter(
                    Expression.eq(
                        Expression.get(FEATURE_KIND_PROPERTY),
                        Expression.literal(FEATURE_KIND_POINT),
                    ),
                ).withProperties(
                    circleRadius(9f),
                    circleColor(Color.WHITE),
                    circleOpacity(1f),
                    circlePitchScale(Property.CIRCLE_PITCH_SCALE_MAP),
                ),
            )
        }

        if (style.getLayer(MapDarkStyle.CURRENT_LOCATION_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(
                    MapDarkStyle.CURRENT_LOCATION_LAYER_ID,
                    MapDarkStyle.CURRENT_LOCATION_SOURCE_ID,
                ).withFilter(
                    Expression.eq(
                        Expression.get(FEATURE_KIND_PROPERTY),
                        Expression.literal(FEATURE_KIND_POINT),
                    ),
                ).withProperties(
                    circleRadius(6f),
                    circleColor(Color.parseColor(MapDarkStyle.LOCATION_BLUE)),
                    circleOpacity(1f),
                    circlePitchScale(Property.CIRCLE_PITCH_SCALE_MAP),
                ),
            )
        }
    }

    private fun createSighStarBitmap(visual: StarVisual): Bitmap {
        val bitmap = Bitmap.createBitmap(STAR_BITMAP_SIZE, STAR_BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = STAR_BITMAP_SIZE / 2f
        val starColor = Color.parseColor(visual.colorHex)

        val glowPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        center,
                        center,
                        center,
                        intArrayOf(
                            Color.argb(180, Color.red(starColor), Color.green(starColor), Color.blue(starColor)),
                            Color.argb(75, Color.red(starColor), Color.green(starColor), Color.blue(starColor)),
                            Color.TRANSPARENT,
                        ),
                        floatArrayOf(0f, 0.52f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawCircle(center, center, center, glowPaint)

        canvas.drawPath(
            starPath(
                center = center,
                majorRadius = 31f,
                diagonalRadius = 24f,
                innerRadius = 15f,
            ),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = starColor
                style = Paint.Style.FILL
            },
        )
        canvas.drawPath(
            starPath(
                center = center,
                majorRadius = 20f,
                diagonalRadius = 15f,
                innerRadius = 10f,
            ),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(DesignSystemColors.STAR_CORE_HEX)
                style = Paint.Style.FILL
            },
        )
        return bitmap
    }

    private fun starPath(
        center: Float,
        majorRadius: Float,
        diagonalRadius: Float,
        innerRadius: Float,
    ): Path =
        Path().apply {
            repeat(16) { index ->
                val radius =
                    when {
                        index % 2 != 0 -> innerRadius
                        index % 4 == 0 -> majorRadius
                        else -> diagonalRadius
                    }
                val angle = -PI / 2.0 + index * PI / 8.0
                val x = center + (cos(angle) * radius).toFloat()
                val y = center + (sin(angle) * radius).toFloat()
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
}
