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
    private val renderDataById = linkedMapOf<Long, CachedPinRenderData>()
    private var lastRenderedPins: List<EmotionPinUiModel>? = null
    private var lastInputPins: List<EmotionPinUiModel>? = null
    private var lastRenderSignature: List<PinRenderSignature>? = null
    private var layerVisible: Boolean? = null
    private var lastMonitoringLoadId: String? = null
    private var renderRevision = 0L
    private var lastPressedId: Long? = null
    private var lastPressedScale = 1f
    private var focusedId: Long? = null
    private var dynamicProperties: DynamicProperties? = null
    private var lastTopPriority = 0

    fun install(style: Style) {
        registeredImageKeys.clear()
        registeredImageSignatures.clear()
        renderDataById.clear()
        lastRenderedPins = null
        lastInputPins = null
        lastRenderSignature = null
        layerVisible = null
        lastMonitoringLoadId = null
        renderRevision = 0L
        lastPressedId = null
        lastPressedScale = 1f
        focusedId = null
        dynamicProperties = null
        lastTopPriority = 0
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
        // Monitoring IDs change during viewport loads even when the map features do not.
        lastMonitoringLoadId = monitoringLoadId
        if (pins === lastInputPins && !imageChanged) {
            this.focusedId = focusedId
            applyDynamicProperties(style, lastTopPriority)
            return false
        }

        // A pin must not enter the source until its asynchronously rasterized icon is ready.
        val renderedPins = pins.filter { it.symbolImageKey() in registeredImageKeys }
        val renderData = renderedPins.map(::renderData)
        val renderSignature = renderData.map(CachedPinRenderData::signature)
        lastInputPins = pins
        lastRenderedPins = renderedPins
        this.focusedId = focusedId
        val sourceChanged = imageChanged || renderSignature != lastRenderSignature
        if (!sourceChanged) {
            lastTopPriority = renderedPins.size
            applyDynamicProperties(style, lastTopPriority)
            return false
        }

        renderRevision++
        val features: List<Feature> =
            renderData.mapIndexed { index, pin ->
                val properties =
                    JsonObject().apply {
                        addProperty(RENDER_REVISION_PROPERTY, renderRevision)
                        addProperty(IMAGE_KEY_PROPERTY, pin.imageKey)
                        addProperty(ROTATION_PROPERTY, pin.signature.rotationDegrees)
                        addProperty(PIN_ID_PROPERTY, pin.signature.id)
                        addProperty(SCALE_PROPERTY, 1.0)
                        addProperty(PRIORITY_PROPERTY, index)
                    }
                Feature.fromGeometry(
                    pin.geometry,
                    properties,
                    pin.signature.id.toString(),
                )
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        lastRenderSignature = renderSignature
        lastTopPriority = renderedPins.size
        applyDynamicProperties(style, lastTopPriority)
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
        lastPressedId = pressedId
        lastPressedScale = scale
        applyDynamicProperties(style, lastTopPriority)
    }

    private fun renderData(pin: EmotionPinUiModel): CachedPinRenderData {
        val signature =
            PinRenderSignature(
                id = pin.id,
                latitude = pin.latitude,
                longitude = pin.longitude,
                imageKey = pin.symbolImageKey(),
                rotationDegrees = pin.rotationDegrees,
            )
        val cached = renderDataById[pin.id]
        if (cached?.signature == signature) {
            renderDataById.remove(pin.id)
            renderDataById[pin.id] = cached
            return cached
        }
        val updated =
            CachedPinRenderData(
                signature = signature,
                geometry = Point.fromLngLat(pin.longitude, pin.latitude),
                imageKey = signature.imageKey,
            )
        renderDataById.remove(pin.id)
        renderDataById[pin.id] = updated
        while (renderDataById.size > MAX_CACHED_PINS) renderDataById.remove(renderDataById.keys.first())
        return updated
    }

    private fun applyDynamicProperties(
        style: Style,
        topPriority: Int,
    ) {
        val next = DynamicProperties(focusedId, lastPressedId, lastPressedScale, topPriority)
        if (next == dynamicProperties) return
        val layer = style.getLayer(LAYER_ID) ?: return
        val focusedScale =
            focusedId?.let { id ->
                Expression.switchCase(
                    Expression.eq(Expression.get(PIN_ID_PROPERTY), Expression.literal(id)),
                    Expression.literal(1.3),
                    Expression.get(SCALE_PROPERTY),
                )
            } ?: Expression.get(SCALE_PROPERTY)
        val iconScale =
            lastPressedId?.let { id ->
                Expression.switchCase(
                    Expression.eq(Expression.get(PIN_ID_PROPERTY), Expression.literal(id)),
                    Expression.literal(lastPressedScale.toDouble()),
                    focusedScale,
                )
            } ?: focusedScale
        val sortPriority =
            focusedId?.let { id ->
                Expression.switchCase(
                    Expression.eq(Expression.get(PIN_ID_PROPERTY), Expression.literal(id)),
                    Expression.literal(topPriority),
                    Expression.get(PRIORITY_PROPERTY),
                )
            } ?: Expression.get(PRIORITY_PROPERTY)
        layer.setProperties(iconSize(iconScale), symbolSortKey(sortPriority))
        dynamicProperties = next
    }

    internal companion object {
        private const val MAX_CACHED_PINS = 4_096
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

    private data class PinRenderSignature(
        val id: Long,
        val latitude: Double,
        val longitude: Double,
        val imageKey: String,
        val rotationDegrees: Double,
    )

    private data class CachedPinRenderData(
        val signature: PinRenderSignature,
        val geometry: Point,
        val imageKey: String,
    )

    private data class DynamicProperties(
        val focusedId: Long?,
        val pressedId: Long?,
        val pressedScale: Float,
        val topPriority: Int,
    )

}
