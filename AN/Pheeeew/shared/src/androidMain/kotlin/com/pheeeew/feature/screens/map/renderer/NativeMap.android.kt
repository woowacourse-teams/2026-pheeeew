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
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.MapCameraState
import com.pheeeew.feature.screens.map.MapCameraActionUiModel
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val INITIAL_ZOOM = 11.0
private const val MINIMUM_ZOOM = 2.0
private const val MAXIMUM_ZOOM = 20.0
private const val FALLBACK_LATITUDE = 37.5665
private const val FALLBACK_LONGITUDE = 126.9780

@Composable
internal actual fun NativeMap(
    state: MapUiModel,
    onCameraStateChanged: (MapCameraState) -> Unit,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCameraStateChanged by rememberUpdatedState(onCameraStateChanged)
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
                    onCameraStateChanged = currentOnCameraStateChanged,
                    onMapError = currentOnMapError,
                    onMapRecovered = currentOnMapRecovered,
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
    private val onCameraStateChanged: (MapCameraState) -> Unit,
    private val onMapError: (MapErrorUiModel) -> Unit,
    private val onMapRecovered: () -> Unit,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var released = false
    private var latestState: MapUiModel? = null
    private var styleLoaded = false
    private var didSetInitialCamera = false
    private var initialCameraUsedFallback = false
    private var lastAppliedCameraCommandId = 0L

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
            readyMap.addOnCameraIdleListener {
                publishCameraState()
            }
            readyMap.setStyle(Style.Builder().fromUri(OPEN_FREE_MAP_STYLE_URL)) { loadedStyle ->
                if (released) return@setStyle
                style = loadedStyle
                AndroidCurrentLocationLayer.install(loadedStyle)
                styleLoaded = true
                onMapRecovered()
                renderLatestState()
            }
        }
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
        publishCameraState()
    }

    private fun publishCameraState() {
        val currentMap = map ?: return
        val position = currentMap.cameraPosition
        val target = position.target ?: return
        onCameraStateChanged(MapCameraState(target.latitude, target.longitude, position.zoom))
    }
}
