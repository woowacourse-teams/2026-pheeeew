package com.pheeeew.feature.screens.map.renderer

import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.JsonObject
import com.pheeeew.feature.screens.map.EmotionPinSymbolImage
import com.pheeeew.feature.screens.map.EmotionPinUiModel
import com.pheeeew.feature.screens.map.symbolImageKey
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.symbolZOrder
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Native MapLibre source and symbol layer for emotion pins. */
internal class EmotionPinSymbolLayer {
    private val registeredImageKeys = mutableSetOf<String>()
    private var lastRenderedPins: List<EmotionPinUiModel>? = null
    private var layerVisible: Boolean? = null
    private var lastMonitoringLoadId: String? = null
    private var lastPressedId: Long? = null
    private var lastPressedScale = 1f
    private var lastFocusedId: Long? = null

    fun install(style: Style) {
        registeredImageKeys.clear()
        lastRenderedPins = null
        layerVisible = null
        lastMonitoringLoadId = null
        lastPressedId = null
        lastPressedScale = 1f
        lastFocusedId = null
        if (style.getSource(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        }
        if (style.getLayer(LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(LAYER_ID, SOURCE_ID)
                    .withProperties(
                        iconImage(Expression.get(IMAGE_KEY_PROPERTY)),
                        iconRotate(Expression.get(ROTATION_PROPERTY)),
                        iconSize(Expression.get(SCALE_PROPERTY)),
                        symbolSortKey(Expression.get(PRIORITY_PROPERTY)),
                        symbolZOrder(Property.SYMBOL_Z_ORDER_SOURCE),
                        iconAnchor(Property.ICON_ANCHOR_CENTER),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
            )
        }
    }

    fun update(
        style: Style,
        pins: List<EmotionPinUiModel>,
        images: List<EmotionPinSymbolImage>,
        visible: Boolean,
        densityDpi: Int,
        monitoringLoadId: String?,
        focusedId: Long?,
    ) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val requiredImageKeys = images.mapTo(mutableSetOf(), EmotionPinSymbolImage::key)
        images.forEach { image ->
            if (style.getImage(image.key) == null) {
                style.addImage(image.key, image.toAndroidBitmap(densityDpi))
            }
        }
        // Drop obsolete raster images so viewport changes cannot grow the style indefinitely.
        registeredImageKeys.filterNot(requiredImageKeys::contains).forEach { key ->
            if (style.getImage(key) != null) style.removeImage(key)
        }
        registeredImageKeys.clear()
        registeredImageKeys.addAll(requiredImageKeys)

        if (layerVisible != visible) {
            style.getLayer(LAYER_ID)?.setProperties(visibility(if (visible) Property.VISIBLE else Property.NONE))
            layerVisible = visible
        }
        // A pin must not enter the source until its asynchronously rasterized icon is ready.
        val renderedPins = pins.filter { it.symbolImageKey() in registeredImageKeys }
        // Loading, notices and other Compose state changes do not change the map's features.
        if (renderedPins == lastRenderedPins && monitoringLoadId == lastMonitoringLoadId &&
            focusedId == lastFocusedId
        ) {
            return
        }
        val features: List<Feature> =
            renderedPins.map { pin ->
                val properties =
                    JsonObject().apply {
                        addProperty("monitoring-load-id", monitoringLoadId)
                        addProperty(IMAGE_KEY_PROPERTY, pin.symbolImageKey())
                        addProperty(ROTATION_PROPERTY, pin.rotationDegrees)
                        addProperty(PIN_ID_PROPERTY, pin.id)
                        addProperty(SCALE_PROPERTY, if (pin.id == focusedId) 1.3 else 1.0)
                        addProperty(PRIORITY_PROPERTY, if (pin.id == focusedId) 1 else 0)
                    }
                Feature.fromGeometry(
                    Point.fromLngLat(pin.longitude, pin.latitude),
                    properties,
                    pin.id.toString(),
                )
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        lastRenderedPins = renderedPins
        lastMonitoringLoadId = monitoringLoadId
        lastFocusedId = focusedId
    }

    fun updatePress(
        style: Style,
        pressedId: Long?,
        scale: Float,
    ) {
        if (pressedId == lastPressedId && scale == lastPressedScale) return
        val layer = style.getLayer(LAYER_ID) ?: return
        val size =
            if (pressedId == null) {
                Expression.get(SCALE_PROPERTY)
            } else {
                Expression.switchCase(
                    Expression.eq(Expression.get(PIN_ID_PROPERTY), Expression.literal(pressedId)),
                    Expression.literal(scale),
                    Expression.get(SCALE_PROPERTY),
                )
            }
        layer.setProperties(iconSize(size))
        lastPressedId = pressedId
        lastPressedScale = scale
    }

    private fun EmotionPinSymbolImage.toAndroidBitmap(densityDpi: Int): Bitmap {
        val pixels = IntArray(width * height)
        var byteIndex = 0
        pixels.indices.forEach { pixelIndex ->
            val red = rgba[byteIndex++].toInt() and 0xff
            val green = rgba[byteIndex++].toInt() and 0xff
            val blue = rgba[byteIndex++].toInt() and 0xff
            val alpha = rgba[byteIndex++].toInt() and 0xff
            pixels[pixelIndex] = Color.argb(alpha, red, green, blue)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
            density = densityDpi
        }
    }

    private companion object {
        const val SOURCE_ID = "emotion-pin-symbol-source"
        const val LAYER_ID = "emotion-pin-symbol-layer"
        const val IMAGE_KEY_PROPERTY = "image-key"
        const val ROTATION_PROPERTY = "rotation-degrees"
        const val PIN_ID_PROPERTY = "pin-id"
        const val SCALE_PROPERTY = "focus-scale"
        const val PRIORITY_PROPERTY = "focus-priority"
    }
}
