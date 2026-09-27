package com.pheeeew.feature.screens.map.renderer

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.feature.screens.map.MapCameraActionUiModel
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel
import com.pheeeew.feature.screens.map.record.location.RECORD_RADIUS_METERS
import com.pheeeew.feature.screens.map.record.location.destination
import com.pheeeew.feature.screens.map.record.location.recordCameraBounds
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

private const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val INITIAL_ZOOM = 11.0
private const val MINIMUM_ZOOM = 2.0
private const val MAXIMUM_ZOOM = 20.0
private const val FALLBACK_LATITUDE = 37.5665
private const val FALLBACK_LONGITUDE = 126.9780

@Composable
internal actual fun NativeMap(
    state: MapUiModel,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    onViewportChanged: (EmotionBounds) -> Unit,
    onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
    modifier: Modifier,
) {
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
                    onViewportChanged = { currentOnViewportChanged(it) },
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
        update = { host.render(state) },
    )
}

private class AndroidFoundationMapHost(
    val mapView: MapView,
    private val onMapError: (MapErrorUiModel) -> Unit,
    private val onMapRecovered: () -> Unit,
    private val onViewportChanged: (EmotionBounds) -> Unit,
    private val onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var released = false
    private var latestState: MapUiModel? = null
    private var styleLoaded = false
    private var didSetInitialCamera = false
    private var initialCameraUsedFallback = false
    private var lastAppliedCameraCommandId = 0L
    private var fittedOrigin: GeoCoordinate? = null
    private var cameraBoundsInstalled = false

    private val mapLoadFailureListener =
        MapView.OnDidFailLoadingMapListener {
            if (!released) onMapError(MapErrorUiModel.StyleLoadFailed)
        }

    init {
        mapView.addOnDidFailLoadingMapListener(mapLoadFailureListener)
        mapView.getMapAsync { readyMap ->
            if (released) return@getMapAsync
            map = readyMap
            readyMap.setMinZoomPreference(MINIMUM_ZOOM)
            readyMap.setMaxZoomPreference(MAXIMUM_ZOOM)
            readyMap.addOnCameraMoveListener {
                publishRecordViewport()
            }
            readyMap.addOnCameraIdleListener {
                publishViewport()
                publishRecordViewport()
            }
            mapView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
                if (sizeChanged && latestState?.isRecordLocationPicking == true) {
                    fittedOrigin = null
                    renderLatestState()
                }
            }
            readyMap.setStyle(Style.Builder().fromUri(OPEN_FREE_MAP_STYLE_URL)) { loadedStyle ->
                if (released) return@setStyle
                style = loadedStyle
                AndroidCurrentLocationLayer.install(loadedStyle)
                styleLoaded = true
                onMapRecovered()
                renderLatestState()
                publishViewport()
            }
        }
    }

    private fun publishViewport() {
        if (released || latestState?.isRecordLocationPicking == true || mapView.width == 0) return
        val bounds = map?.projection?.visibleRegion?.latLngBounds ?: return
        val value =
            runCatching {
                EmotionBounds(bounds.longitudeWest, bounds.latitudeSouth, bounds.longitudeEast, bounds.latitudeNorth)
            }.getOrNull()
                ?: return
        onViewportChanged(value)
    }

    fun render(state: MapUiModel) {
        latestState = state
        renderLatestState()
    }

    fun release() {
        if (released) return
        released = true
        mapView.removeOnDidFailLoadingMapListener(mapLoadFailureListener)
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
        map = null
        style = null
    }

    private fun renderLatestState() {
        if (!styleLoaded || released) return
        val currentMap = map ?: return
        val state = latestState ?: return
        val currentLocation = (state.locationState as? LocationState.Available)?.location
        AndroidCurrentLocationLayer.update(style, currentLocation)
        val point =
            currentLocation?.let { LatLng(it.latitude, it.longitude) }
                ?: LatLng(FALLBACK_LATITUDE, FALLBACK_LONGITUDE)
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
            state.cameraCommand?.let { lastAppliedCameraCommandId = it.id }
            val origin = state.recordOrigin ?: return
            if (fittedOrigin != origin && mapView.width > 0 && mapView.height > 0) {
                fittedOrigin = origin
                currentMap.setLatLngBoundsForCameraTarget(null)
                cameraBoundsInstalled = false
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
                currentMap.setMinZoomPreference(currentMap.cameraPosition.zoom - 1.0)
                val cameraBounds = recordCameraBounds(origin)
                currentMap.setLatLngBoundsForCameraTarget(
                    LatLngBounds.from(cameraBounds.north, cameraBounds.east, cameraBounds.south, cameraBounds.west),
                )
                cameraBoundsInstalled = true
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
            val update =
                when (command.action) {
                    MapCameraActionUiModel.MoveToCoordinate -> {
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(command.latitude, command.longitude),
                            command.value,
                        )
                    }

                    MapCameraActionUiModel.ZoomBy -> {
                        CameraUpdateFactory.zoomBy(command.value)
                    }
                }
            currentMap.animateCamera(update, 350)
        }
    }

    private fun publishRecordViewport() {
        val state = latestState ?: return
        if (!state.isRecordLocationPicking || !styleLoaded || mapView.width == 0) return
        val origin = state.recordOrigin ?: return
        val currentMap = map ?: return
        val center = currentMap.projection.toScreenLocation(LatLng(origin.latitude, origin.longitude))
        val north = destination(origin, RECORD_RADIUS_METERS, 0.0)
        val edge = currentMap.projection.toScreenLocation(LatLng(north.latitude, north.longitude))
        val density = mapView.resources.displayMetrics.density
        val radius = hypot(edge.x - center.x, edge.y - center.y) / density
        if (radius > 0 && radius.isFinite()) {
            onRecordViewportChanged(center.x / density, center.y / density, radius)
        }
    }
}
