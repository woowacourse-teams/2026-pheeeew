package com.pheeeew.feature.screens.map.renderer

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.view.Gravity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.feature.screens.map.HighlightedPinPosition
import com.pheeeew.feature.screens.map.MapCameraActionUiModel
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel
import com.pheeeew.feature.screens.map.record.location.RECORD_RADIUS_METERS
import com.pheeeew.feature.screens.map.record.location.constrainToRecordRadius
import com.pheeeew.feature.screens.map.record.location.destination
import com.pheeeew.feature.screens.map.record.location.recordCameraBounds
import com.pheeeew.feature.screens.map.symbolImageKey
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val MAP_FONT_REGULAR_ASSET_URL =
    "asset://composeResources/pheeeew.shared.generated.resources/font/tap_noto_700.ttf"
private const val MAP_FONT_BOLD_ASSET_URL =
    "asset://composeResources/pheeeew.shared.generated.resources/font/tap_noto_900.ttf"
private const val INITIAL_ZOOM = 11.0
private const val MINIMUM_ZOOM = 2.0
private const val MAXIMUM_ZOOM = 20.0
private const val FALLBACK_LATITUDE = 37.4409230460675
private const val FALLBACK_LONGITUDE = 127.147538132656

@Composable
internal actual fun NativeMap(
    state: MapUiModel,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
    onViewportChanged: (EmotionMapBounds) -> Unit,
    onEmotionPinClick: (Long) -> Unit,
    onMapBackgroundClick: () -> Unit,
    onHighlightedPinPositionChanged: (HighlightedPinPosition?) -> Unit,
    onContentPresented: (String, List<String>) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.getTop(density)
    val navigationBarInset = WindowInsets.navigationBars.getBottom(density)
    // NavHost restores saveable state, but recreates the native MapView on tab return.
    var savedCamera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var savedCameraCommandId by rememberSaveable { mutableLongStateOf(0L) }
    val currentOnContentPresented by rememberUpdatedState(onContentPresented)
    val currentOnEmotionPinClick by rememberUpdatedState(onEmotionPinClick)
    val currentOnMapBackgroundClick by rememberUpdatedState(onMapBackgroundClick)
    val currentOnHighlightPosition by rememberUpdatedState(onHighlightedPinPositionChanged)
    val currentOnMapError by rememberUpdatedState(onMapError)
    val currentOnMapRecovered by rememberUpdatedState(onMapRecovered)
    val hostResult =
        remember(context) {
            runCatching {
                MapLibre.getInstance(context.applicationContext)
                AndroidFoundationMapHost(
                    MapView(context).apply {
                        setBackgroundColor(Color.BLACK)
                        onCreate(null)
                    },
                    onMapError = currentOnMapError,
                    onMapRecovered = currentOnMapRecovered,
                    onRecordViewportChanged = onRecordViewportChanged,
                    onViewportChanged = onViewportChanged,
                    onEmotionPinClick = { currentOnEmotionPinClick(it) },
                    onMapBackgroundClick = { currentOnMapBackgroundClick() },
                    onHighlightedPinPositionChanged = { currentOnHighlightPosition(it) },
                    onContentPresented = { token, count -> currentOnContentPresented(token, count) },
                    restoredCamera = savedCamera,
                    restoredCameraCommandId = savedCameraCommandId,
                    onCameraSaved = { camera, commandId ->
                        savedCamera = camera
                        savedCameraCommandId = commandId
                    },
                )
            }
        }
    val host = hostResult.getOrNull()

    if (host == null) {
        LaunchedEffect(Unit) { currentOnMapError(MapErrorUiModel.RendererUnavailable) }
        return
    }

    DisposableEffect(host, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> host.mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> host.mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> host.mapView.onPause()
                    Lifecycle.Event.ON_STOP -> host.mapView.onStop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) host.mapView.onStart()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) host.mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            host.release()
        }
    }

    AndroidView(
        factory = { host.mapView },
        modifier = modifier,
        update = { host.render(state, statusBarInset, navigationBarInset) },
    )
}

private class AndroidFoundationMapHost(
    val mapView: MapView,
    private val onMapError: (MapErrorUiModel) -> Unit,
    private val onMapRecovered: () -> Unit,
    private val onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
    private val onViewportChanged: (EmotionMapBounds) -> Unit,
    private val onEmotionPinClick: (Long) -> Unit,
    private val onMapBackgroundClick: () -> Unit,
    private val onHighlightedPinPositionChanged: (HighlightedPinPosition?) -> Unit,
    private val onContentPresented: (String, List<String>) -> Unit,
    private val restoredCamera: DoubleArray?,
    restoredCameraCommandId: Long,
    private val onCameraSaved: (DoubleArray, Long) -> Unit,
) {
    private val emotionPinSymbolLayer = EmotionPinSymbolLayer()
    private val recordStampLayer = AndroidRecordStampLayer()
    private val recordRangeLayer = AndroidRecordRangeLayer()
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var released = false
    private var latestState: MapUiModel? = null
    private var styleLoaded = false
    private var didSetInitialCamera = false
    private var initialCameraUsedFallback = false
    private var lastAppliedCameraCommandId = restoredCameraCommandId
    private var fittedOrigin: GeoCoordinate? = null
    private var cameraBoundsInstalled = false
    private var statusBarInsetPx = 0
    private var navigationBarInsetPx = 0
    private var didConfigureKoreanFontFaces = false
    private var pendingOriginalStyleJson: String? = null
    private var isRestoringOriginalStyle = false

    private val idleListener = MapView.OnDidBecomeIdleListener { publishContent() }

    private fun publishContent() {
        val state = latestState ?: return
        val token = state.emotionContentLoad?.loadId ?: return
        val currentMap = map ?: return
        if (released || !styleLoaded || state.isRecordLocationPicking || mapView.width <= 0 ||
            mapView.height <= 0
        ) {
            return
        }
        val imageKeys = state.emotionPinSymbolImages.mapTo(mutableSetOf()) { it.key }
        if (state.emotionPins.any { it.symbolImageKey() !in imageKeys }) return
        val features =
            currentMap.queryRenderedFeatures(
                RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
                "emotion-pin-symbol-layer",
            )
        // Reject a native frame that still contains the previous asynchronous GeoJSON update.
        if (features.any {
                it.getProperty("monitoring-load-id")?.takeUnless { value -> value.isJsonNull }?.asString !=
                    token
            }
        ) {
            return
        }
        val ids = features.mapNotNull { it.id()?.takeIf { id -> id.toLongOrNull() != null } }.distinct()
        onContentPresented(token, ids)
    }

    private val mapLoadFailureListener =
        MapView.OnDidFailLoadingMapListener {
            val originalStyleJson = pendingOriginalStyleJson
            val currentMap = map
            if (!released && originalStyleJson != null && !isRestoringOriginalStyle && currentMap != null) {
                isRestoringOriginalStyle = true
                currentMap.setStyle(Style.Builder().fromJson(originalStyleJson)) { restoredStyle ->
                    if (released) return@setStyle
                    pendingOriginalStyleJson = null
                    isRestoringOriginalStyle = false
                    installLoadedStyle(restoredStyle)
                }
            } else if (!released) {
                pendingOriginalStyleJson = null
                isRestoringOriginalStyle = false
                onMapError(MapErrorUiModel.StyleLoadFailed)
            }
        }

    init {
        mapView.addOnDidFailLoadingMapListener(mapLoadFailureListener)
        mapView.addOnDidBecomeIdleListener(idleListener)
        mapView.getMapAsync { readyMap ->
            if (released) return@getMapAsync
            map = readyMap
            // MapLibre dispatches this only after confirming a single tap (not a double tap).
            readyMap.addOnMapClickListener { coordinate ->
                if (released || latestState?.isRecordLocationPicking == true) return@addOnMapClickListener false
                val point = readyMap.projection.toScreenLocation(coordinate)
                val id =
                    readyMap
                        .queryRenderedFeatures(point, "emotion-pin-symbol-layer")
                        .firstOrNull()
                        ?.id()
                        ?.toLongOrNull()
                if (id != null) onEmotionPinClick(id) else onMapBackgroundClick()
                true
            }
            readyMap.setMinZoomPreference(MINIMUM_ZOOM)
            readyMap.setMaxZoomPreference(MAXIMUM_ZOOM)
            readyMap.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.START
                setAttributionTintColor(Color.WHITE)
                isCompassEnabled = true
                compassGravity = Gravity.TOP or Gravity.END
                setCompassFadeFacingNorth(true)
            }
            applyCompassMargins()
            applyAttributionMargins()
            readyMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && didSetInitialCamera) {
                    initialCameraUsedFallback = false
                    saveCamera()
                }
            }
            readyMap.addOnCameraMoveListener {
                saveCamera()
                publishHighlightedPinPosition()
                publishRecordViewport()
            }
            readyMap.addOnCameraIdleListener {
                saveCamera()
                publishRecordViewport()
                publishViewport()
            }
            mapView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
                if (sizeChanged) {
                    if (latestState?.isRecordLocationPicking == true) fittedOrigin = null
                    renderLatestState()
                    publishViewport()
                }
            }
            readyMap.setStyle(Style.Builder().fromUri(OPEN_FREE_MAP_STYLE_URL)) { loadedStyle ->
                if (released) return@setStyle
                if (!didConfigureKoreanFontFaces) {
                    didConfigureKoreanFontFaces = true
                    val originalStyleJson = loadedStyle.json
                    val styleJson = withKoreanFontFaces(originalStyleJson)
                    if (styleJson != null) {
                        pendingOriginalStyleJson = originalStyleJson
                        isRestoringOriginalStyle = false
                        readyMap.setStyle(Style.Builder().fromJson(styleJson)) configuredStyle@{ configuredStyle ->
                            if (released) return@configuredStyle
                            pendingOriginalStyleJson = null
                            installLoadedStyle(configuredStyle)
                        }
                    } else {
                        installLoadedStyle(loadedStyle)
                    }
                } else {
                    installLoadedStyle(loadedStyle)
                }
            }
        }
    }

    fun render(
        state: MapUiModel,
        statusBarInset: Int,
        navigationBarInset: Int,
    ) {
        statusBarInsetPx = statusBarInset
        navigationBarInsetPx = navigationBarInset
        applyCompassMargins()
        applyAttributionMargins()
        latestState = state
        renderLatestState()
        publishViewport()
    }

    fun release() {
        if (released) return
        saveCamera()
        released = true
        mapView.removeOnDidFailLoadingMapListener(mapLoadFailureListener)
        mapView.removeOnDidBecomeIdleListener(idleListener)
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
        map = null
        style = null
    }

    private fun saveCamera() {
        if (!didSetInitialCamera || released) return
        val camera = map?.cameraPosition ?: return
        val target = camera.target ?: return
        onCameraSaved(
            doubleArrayOf(
                target.latitude,
                target.longitude,
                camera.zoom,
                camera.bearing,
                camera.tilt,
                if (initialCameraUsedFallback) 1.0 else 0.0,
            ),
            lastAppliedCameraCommandId,
        )
    }

    private fun withKoreanFontFaces(styleJson: String): String? =
        runCatching {
            val style = JSONObject(styleJson)
            val fontFaces = JSONObject()
            mapOf(
                "Noto Sans Regular" to MAP_FONT_REGULAR_ASSET_URL,
                "Noto Sans Italic" to MAP_FONT_REGULAR_ASSET_URL,
                "Noto Sans Bold" to MAP_FONT_BOLD_ASSET_URL,
            ).forEach { (fontName, fontUrl) ->
                val face =
                    JSONObject()
                        .put("url", fontUrl)
                        .put("unicode-range", JSONArray(KOREAN_UNICODE_RANGES))
                fontFaces.put(fontName, JSONArray().put(face))
            }
            style.put("font-faces", fontFaces)

            val layers = style.optJSONArray("layers") ?: return@runCatching style.toString()
            for (index in 0 until layers.length()) {
                val layer = layers.optJSONObject(index) ?: continue
                if (layer.optString("type") != "symbol") continue
                val layerId = layer.optString("id")
                val layout = layer.optJSONObject("layout") ?: continue
                if (layerId.startsWith("highway-shield-") || layerId.startsWith("road_shield_")) {
                    layout.put("visibility", "none")
                }
                val sizeScale =
                    when {
                        layerId.startsWith("label_country_") -> 0.76
                        layerId in CITY_LABEL_LAYER_IDS -> 1.0
                        layerId == "label_other" -> 1.05
                        else -> 0.92
                    }
                scaleTextSize(layout, sizeScale)
                if (layerId.startsWith("label_country_")) layer.put("maxzoom", 12)
                if (layerId == "label_other") {
                    layer.put("minzoom", 7)
                    layout.remove("text-transform")
                }
                layout.optJSONArray("text-font")?.let { fonts ->
                    for (fontIndex in 0 until fonts.length()) {
                        if (fonts.optString(fontIndex) == "Noto Sans Bold" ||
                            (layerId == "label_other" && fonts.optString(fontIndex) == "Noto Sans Italic")
                        ) {
                            fonts.put(fontIndex, "Noto Sans Regular")
                        }
                    }
                }
            }
            style.toString()
        }.getOrNull()

    private fun scaleTextSize(
        layout: JSONObject,
        scale: Double,
    ) {
        when (val textSize = layout.opt("text-size")) {
            is Number -> {
                layout.put("text-size", textSize.toDouble() * scale)
            }

            is JSONArray -> {
                when (textSize.optString(0)) {
                    "interpolate", "interpolate-hcl", "interpolate-lab" -> {
                        for (index in 4 until textSize.length() step 2) {
                            val output = textSize.opt(index)
                            if (output is Number) textSize.put(index, output.toDouble() * scale)
                        }
                    }

                    "step" -> {
                        for (index in 2 until textSize.length() step 2) {
                            val output = textSize.opt(index)
                            if (output is Number) textSize.put(index, output.toDouble() * scale)
                        }
                    }
                }
            }
        }
    }

    private fun installLoadedStyle(loadedStyle: Style) {
        style = loadedStyle
        AndroidMapAppearance.apply(loadedStyle)
        emotionPinSymbolLayer.install(loadedStyle)
        recordRangeLayer.install(loadedStyle)
        AndroidCurrentLocationLayer.install(loadedStyle)
        recordStampLayer.install(loadedStyle)
        styleLoaded = true
        onMapRecovered()
        renderLatestState()
        publishViewport()
    }

    private companion object {
        val CITY_LABEL_LAYER_IDS = setOf("label_city", "label_city_capital", "label_town", "label_village")
        val KOREAN_UNICODE_RANGES =
            listOf("U+1100-11FF", "U+3130-318F", "U+A960-A97F", "U+AC00-D7AF", "U+D7B0-D7FF")
    }

    private fun applyCompassMargins() {
        map?.uiSettings?.setCompassMargins(
            0,
            statusBarInsetPx + (16f * mapView.resources.displayMetrics.density).roundToInt(),
            (16f * mapView.resources.displayMetrics.density).roundToInt(),
            0,
        )
    }

    private fun applyAttributionMargins() {
        map?.uiSettings?.setAttributionMargins(
            (16f * mapView.resources.displayMetrics.density).roundToInt(),
            0,
            0,
            navigationBarInsetPx + (16f * mapView.resources.displayMetrics.density).roundToInt(),
        )
    }

    private fun renderLatestState() {
        if (!styleLoaded || released) return
        val currentMap = map ?: return
        val state = latestState ?: return
        val currentLocation = (state.locationState as? LocationState.Available)?.location
        AndroidCurrentLocationLayer.update(style, currentLocation)
        recordRangeLayer.update(style, state.recordOrigin.takeIf { state.isRecordLocationPicking })
        style?.let { loadedStyle ->
            emotionPinSymbolLayer.update(
                style = loadedStyle,
                pins = state.emotionPins,
                images = state.emotionPinSymbolImages,
                visible = true,
                densityDpi = mapView.resources.displayMetrics.densityDpi,
                monitoringLoadId = state.emotionContentLoad?.loadId,
                focusedId = state.focusedEmotionId,
            )
            emotionPinSymbolLayer.updatePress(loadedStyle, state.pressedEmotionId, state.pressedEmotionScale)
            recordStampLayer.update(loadedStyle, state.recordPreviewPin, state.recordPreviewScale)
        }
        val point =
            currentLocation?.let { LatLng(it.latitude, it.longitude) }
                ?: LatLng(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
        if (!didSetInitialCamera && restoredCamera != null) {
            currentMap.cameraPosition =
                CameraPosition
                    .Builder()
                    .target(LatLng(restoredCamera[0], restoredCamera[1]))
                    .zoom(restoredCamera[2])
                    .bearing(restoredCamera[3])
                    .tilt(restoredCamera[4])
                    .build()
            didSetInitialCamera = true
            initialCameraUsedFallback = restoredCamera[5] == 1.0
        }
        if (!didSetInitialCamera || (initialCameraUsedFallback && currentLocation != null)) {
            currentMap.cameraPosition =
                CameraPosition
                    .Builder()
                    .target(point)
                    .zoom(INITIAL_ZOOM)
                    .build()
            didSetInitialCamera = true
            initialCameraUsedFallback = currentLocation == null
        }
        val picking = state.isRecordLocationPicking
        currentMap.uiSettings.apply {
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
            isRotateGesturesEnabled = !picking
            isTiltGesturesEnabled = !picking
            isDoubleTapGesturesEnabled = true
            isQuickZoomGesturesEnabled = true
        }
        if (picking) {
            val origin = state.recordOrigin ?: return
            if (fittedOrigin != origin && mapView.width > 0 && mapView.height > 0) {
                fittedOrigin = origin
                currentMap.setLatLngBoundsForCameraTarget(null)
                cameraBoundsInstalled = false
                state.cameraCommand?.let { lastAppliedCameraCommandId = it.id }
                currentMap.setMinZoomPreference(MINIMUM_ZOOM)
                val north = destination(origin, RECORD_RADIUS_METERS, 0.0)
                val south = destination(origin, RECORD_RADIUS_METERS, PI)
                val east = destination(origin, RECORD_RADIUS_METERS, PI / 2)
                val west = destination(origin, RECORD_RADIUS_METERS, -PI / 2)
                val bounds =
                    LatLngBounds
                        .Builder()
                        .include(LatLng(north.latitude, east.longitude))
                        .include(LatLng(south.latitude, west.longitude))
                        .build()
                currentMap.cancelTransitions()
                currentMap.cameraPosition =
                    CameraPosition
                        .Builder(currentMap.cameraPosition)
                        .bearing(0.0)
                        .tilt(0.0)
                        .build()
                currentMap.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        (mapView.width * 0.12).toInt(),
                        (mapView.height * 0.22).toInt(),
                        (mapView.width * 0.12).toInt(),
                        (mapView.height * 0.22).toInt(),
                    ),
                )
                currentMap.setMinZoomPreference(currentMap.cameraPosition.zoom)
                val cameraBounds = recordCameraBounds(origin)
                currentMap.setLatLngBoundsForCameraTarget(
                    LatLngBounds.from(cameraBounds.north, cameraBounds.east, cameraBounds.south, cameraBounds.west),
                )
                cameraBoundsInstalled = true
            }
            state.cameraCommand?.takeIf { it.id > lastAppliedCameraCommandId }?.let { command ->
                lastAppliedCameraCommandId = command.id
                if (command.action == MapCameraActionUiModel.MoveToCoordinate) {
                    val target = constrainToRecordRadius(origin, GeoCoordinate(command.latitude, command.longitude))
                    currentMap.animateCamera(
                        CameraUpdateFactory.newLatLng(LatLng(target.latitude, target.longitude)),
                    )
                }
            }
            publishRecordViewport()
            return
        }
        if (cameraBoundsInstalled) {
            currentMap.setLatLngBoundsForCameraTarget(null)
            currentMap.setMinZoomPreference(MINIMUM_ZOOM)
            cameraBoundsInstalled = false
        }
        fittedOrigin = null
        state.cameraCommand?.takeIf { it.id > lastAppliedCameraCommandId }?.let { command ->
            lastAppliedCameraCommandId = command.id
            currentMap.cancelTransitions()
            val bottomPadding = mapView.height * (1.0 - 2.0 * command.verticalPosition).coerceIn(0.0, 1.0)
            val update =
                when (command.action) {
                    MapCameraActionUiModel.MoveToCoordinate -> {
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition
                                .Builder(currentMap.cameraPosition)
                                .target(LatLng(command.latitude, command.longitude))
                                .zoom(command.value)
                                .padding(0.0, 0.0, 0.0, bottomPadding)
                                .build(),
                        )
                    }

                    MapCameraActionUiModel.ZoomBy -> {
                        CameraUpdateFactory.zoomBy(command.value)
                    }
                }
            if (command.action == MapCameraActionUiModel.MoveToCoordinate && bottomPadding > 0.0) {
                currentMap.animateCamera(
                    update,
                    350,
                    object : MapLibreMap.CancelableCallback {
                        private var cleared = false

                        override fun onFinish() = clearPaddingOnce()

                        override fun onCancel() = clearPaddingOnce()

                        private fun clearPaddingOnce() {
                            if (cleared) return
                            cleared = true
                            clearFocusPadding(currentMap)
                        }
                    },
                )
            } else {
                currentMap.animateCamera(update, 350)
            }
        }
    }

    private fun clearFocusPadding(currentMap: MapLibreMap) {
        if (released) return
        if (currentMap.cameraPosition.padding?.any { it != 0.0 } != true) return
        // Preserve the visible map while removing padding from subsequent gestures and commands.
        val center = currentMap.projection.fromScreenLocation(PointF(mapView.width / 2f, mapView.height / 2f))
        currentMap.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition
                    .Builder(currentMap.cameraPosition)
                    .target(center)
                    .padding(0.0, 0.0, 0.0, 0.0)
                    .build(),
            ),
        )
    }

    private fun publishHighlightedPinPosition() {
        val state = latestState
        val currentMap = map
        val pin = state?.emotionPins?.firstOrNull { it.id == state.highlightedEmotionId }
        if (!styleLoaded || released || currentMap == null || pin == null ||
            state.isRecordLocationPicking || mapView.width == 0 ||
            style?.getImage(pin.symbolImageKey()) == null
        ) {
            onHighlightedPinPositionChanged(null)
            return
        }
        val point = currentMap.projection.toScreenLocation(LatLng(pin.latitude, pin.longitude))
        val density = mapView.resources.displayMetrics.density
        onHighlightedPinPositionChanged(HighlightedPinPosition(pin.id, point.x / density, point.y / density))
    }

    private fun publishRecordViewport() {
        val state = latestState ?: return
        if (!state.isRecordLocationPicking || !styleLoaded || mapView.width == 0) return
        val origin = state.recordOrigin ?: return
        val currentMap = map ?: return
        val originPoint = currentMap.projection.toScreenLocation(LatLng(origin.latitude, origin.longitude))
        val north = destination(origin, RECORD_RADIUS_METERS, 0.0)
        val edge = currentMap.projection.toScreenLocation(LatLng(north.latitude, north.longitude))
        val density = mapView.resources.displayMetrics.density
        val radius = hypot(edge.x - originPoint.x, edge.y - originPoint.y) / density
        if (radius > 0 && radius.isFinite()) {
            onRecordViewportChanged(originPoint.x / density, originPoint.y / density, radius)
        }
    }

    private fun publishViewport() {
        publishHighlightedPinPosition()
        val currentMap = map ?: return
        if (!styleLoaded || released || mapView.width <= 0 || mapView.height <= 0) return
        val bounds = currentMap.projection.visibleRegion.latLngBounds
        val queryBounds =
            EmotionMapBounds(
                minLongitude = bounds.longitudeWest,
                minLatitude = bounds.latitudeSouth,
                maxLongitude = bounds.longitudeEast,
                maxLatitude = bounds.latitudeNorth,
            )
        if (queryBounds.isValid()) onViewportChanged(queryBounds)
    }
}
