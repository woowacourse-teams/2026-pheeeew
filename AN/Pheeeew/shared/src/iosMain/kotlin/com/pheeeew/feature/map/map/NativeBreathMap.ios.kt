package com.pheeeew.feature.map.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.feature.map.MapRenderState

@Composable
internal actual fun NativeBreathMap(
    state: MapRenderState,
    cameraCommand: MapCameraCommand?,
    onSighClick: (String) -> Unit,
    onBoundsChanged: (SighBounds) -> Unit,
    onMapError: (MapError) -> Unit,
    onMapRecovered: () -> Unit,
    onProjectionChanged: (MapProjectionSnapshot) -> Unit,
    modifier: Modifier,
) {
    val currentOnSighClick by rememberUpdatedState(onSighClick)
    val currentOnBoundsChanged by rememberUpdatedState(onBoundsChanged)
    val currentOnMapError by rememberUpdatedState(onMapError)
    val currentOnMapRecovered by rememberUpdatedState(onMapRecovered)
    val currentOnProjectionChanged by rememberUpdatedState(onProjectionChanged)
    val eventSink =
        remember {
            object : IosMapEventSink {
                override fun onSighClick(id: String) = currentOnSighClick(id)

                override fun onBoundsChanged(
                    minLongitude: Double,
                    minLatitude: Double,
                    maxLongitude: Double,
                    maxLatitude: Double,
                ) = currentOnBoundsChanged(
                    SighBounds.fromViewport(
                        west = minLongitude,
                        south = minLatitude,
                        east = maxLongitude,
                        north = maxLatitude,
                    ),
                )

                override fun onRendererUnavailable() = currentOnMapError(MapError.RendererUnavailable)

                override fun onStyleLoadFailed() = currentOnMapError(MapError.StyleLoadFailed)

                override fun onMapRecovered() = currentOnMapRecovered()

                private var revision = 0L

                override fun onProjectionChanged(
                    points: List<IosMapScreenPoint>,
                    cameraIdle: Boolean,
                ) {
                    revision += 1L
                    currentOnProjectionChanged(
                        MapProjectionSnapshot(
                            revision = revision,
                            points =
                                points.associate { point ->
                                    point.id to MapScreenPoint(point.id, point.xPx.toFloat(), point.yPx.toFloat())
                                },
                            cameraIdle = cameraIdle,
                        ),
                    )
                }
            }
        }

    UIKitView(
        factory = { IosMapBridge.createMapView(eventSink) },
        modifier = modifier,
        update = { mapView ->
            IosMapBridge.updateMapView(
                mapView = mapView,
                state = state.toIosRenderState(cameraCommand),
            )
        },
        onRelease = IosMapBridge::releaseMapView,
        properties =
            UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = false,
            ),
    )
}

private fun MapRenderState.toIosRenderState(cameraCommand: MapCameraCommand?): IosMapRenderState {
    val location = MapRenderRules.currentLocation(this)
    val center = MapRenderRules.initialCenter(this)

    return IosMapRenderState(
        sighMarkers =
            MapRenderRules.renderableSighMarkers(sighMarkers).map { marker ->
                IosSighMarker(
                    id = marker.id,
                    latitude = marker.latitude,
                    longitude = marker.longitude,
                    visual = marker.visual,
                )
            },
        currentLocation =
            location?.let {
                IosCurrentLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracyMeters.toDouble(),
                )
            },
        initialCenter =
            center?.let {
                IosMapCoordinate(
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            },
        initialCenterIsProvisional = MapRenderRules.initialCenterIsProvisional(this),
        focusRequest =
            focusRequest
                ?.takeIf {
                    it.latitude.isFinite() &&
                        it.longitude.isFinite() &&
                        it.latitude in -90.0..90.0 &&
                        it.longitude in -180.0..180.0
                }?.let {
                    IosMapFocusRequest(
                        id = it.id,
                        latitude = it.latitude,
                        longitude = it.longitude,
                    )
                },
        cameraCommand = cameraCommand?.toIosCameraCommand(),
    )
}

private fun MapCameraCommand.toIosCameraCommand(): IosMapCameraCommand =
    when (this) {
        is MapCameraCommand.MoveToCurrentLocation -> {
            IosMapCameraCommand(
                id = id,
                kind = IosMapCameraCommandKind.MoveToCurrentLocation,
                delta = 0.0,
                zoom = zoom,
            )
        }

        is MapCameraCommand.ZoomBy -> {
            IosMapCameraCommand(
                id = id,
                kind = IosMapCameraCommandKind.ZoomBy,
                delta = delta,
                zoom = null,
            )
        }
    }
