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
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Native MapLibre source and symbol layer for emotion pins. */
internal class EmotionPinSymbolLayer {
    private val registeredImageKeys = mutableSetOf<String>()
    private var renderedPins: List<EmotionPinUiModel>? = null
    private var layerVisible: Boolean? = null

    fun install(style: Style) {
        registeredImageKeys.clear()
        renderedPins = null
        layerVisible = null
        if (style.getSource(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        }
        if (style.getLayer(LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(LAYER_ID, SOURCE_ID)
                    .withProperties(
                        iconImage(Expression.get(IMAGE_KEY_PROPERTY)),
                        iconRotate(Expression.get(ROTATION_PROPERTY)),
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
        // Keep the source while selecting a location and avoid rebuilding it for unrelated UI updates.
        if (renderedPins == pins) return
        val features =
            pins.map { pin ->
                val properties =
                    JsonObject().apply {
                        addProperty(IMAGE_KEY_PROPERTY, pin.symbolImageKey())
                        addProperty(ROTATION_PROPERTY, pin.rotationDegrees)
                    }
                Feature.fromGeometry(
                    Point.fromLngLat(pin.longitude, pin.latitude),
                    properties,
                    pin.id.toString(),
                )
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        renderedPins = pins.toList()
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
    }
}
