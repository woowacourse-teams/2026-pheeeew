package com.pheeeew.feature.screens.map.renderer

import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
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
    private val registeredImageSignatures = mutableMapOf<String, ImageSignature>()
    private var lastRenderedPins: List<EmotionPinUiModel>? = null
    private var layerVisible: Boolean? = null
    private var lastMonitoringLoadId: String? = null
    private var renderRevision = 0L
    private var lastPressedId: Long? = null
    private var lastPressedScale = 1f
    private var lastFocusedId: Long? = null

    fun install(style: Style) {
        registeredImageKeys.clear()
        registeredImageSignatures.clear()
        lastRenderedPins = null
        layerVisible = null
        lastMonitoringLoadId = null
        renderRevision = 0L
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
        monitoringLoadId: String?,
        focusedId: Long?,
    ): Boolean {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return false
        var imageChanged = false
        images.forEach { image ->
            val bitmap = image.androidImageBitmap?.asAndroidBitmap() ?: return@forEach
            val signature = ImageSignature(image.width, image.height, bitmap.density)
            if (registeredImageSignatures[image.key] != signature) {
                if (style.getImage(image.key) != null) style.removeImage(image.key)
                style.addImage(image.key, bitmap)
                registeredImageSignatures[image.key] = signature
                imageChanged = true
            }
            registeredImageKeys += image.key
        }

        if (layerVisible != visible) {
            style.getLayer(LAYER_ID)?.setProperties(visibility(if (visible) Property.VISIBLE else Property.NONE))
            layerVisible = visible
        }
        // A pin must not enter the source until its asynchronously rasterized icon is ready.
        val renderedPins = pins.filter { it.symbolImageKey() in registeredImageKeys }
        // Monitoring IDs change during viewport loads even when the map features do not.
        lastMonitoringLoadId = monitoringLoadId
        if (renderedPins == lastRenderedPins && focusedId == lastFocusedId && !imageChanged) {
            return false
        }
        renderRevision++
        val topPriority = renderedPins.size
        val features: List<Feature> =
            renderedPins.mapIndexed { index, pin ->
                val properties =
                    JsonObject().apply {
                        addProperty(RENDER_REVISION_PROPERTY, renderRevision)
                        addProperty(IMAGE_KEY_PROPERTY, pin.symbolImageKey())
                        addProperty(ROTATION_PROPERTY, pin.rotationDegrees)
                        addProperty(PIN_ID_PROPERTY, pin.id)
                        addProperty(SCALE_PROPERTY, if (pin.id == focusedId) 1.3 else 1.0)
                        addProperty(PRIORITY_PROPERTY, if (pin.id == focusedId) topPriority else index)
                    }
                Feature.fromGeometry(
                    Point.fromLngLat(pin.longitude, pin.latitude),
                    properties,
                    pin.id.toString(),
                )
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        lastRenderedPins = renderedPins
        lastFocusedId = focusedId
        return true
    }

    fun isCurrentLoad(loadId: String): Boolean = lastMonitoringLoadId == loadId

    fun hasImagesFor(pins: List<EmotionPinUiModel>): Boolean = pins.all { it.symbolImageKey() in registeredImageKeys }

    fun isCurrentFrame(features: List<Feature>): Boolean =
        features.all { feature ->
            feature.getProperty(RENDER_REVISION_PROPERTY)?.takeUnless { it.isJsonNull }?.asLong == renderRevision
        }

    fun pruneObsoleteImages(
        style: Style,
        desiredImageKeys: Set<String>,
    ) {
        val referencedKeys =
            desiredImageKeys + lastRenderedPins.orEmpty().map(EmotionPinUiModel::symbolImageKey)
        val obsoleteKeys = registeredImageKeys - referencedKeys
        obsoleteKeys.forEach { key ->
            if (style.getImage(key) != null) style.removeImage(key)
            registeredImageSignatures.remove(key)
        }
        registeredImageKeys.removeAll(obsoleteKeys)
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

    internal companion object {
        const val SOURCE_ID = "emotion-pin-symbol-source"
        const val LAYER_ID = "emotion-pin-symbol-layer"
        const val IMAGE_KEY_PROPERTY = "image-key"
        const val ROTATION_PROPERTY = "rotation-degrees"
        const val PIN_ID_PROPERTY = "pin-id"
        const val SCALE_PROPERTY = "focus-scale"
        const val PRIORITY_PROPERTY = "focus-priority"
        const val RENDER_REVISION_PROPERTY = "render-revision"
    }

    private data class ImageSignature(
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )
}
