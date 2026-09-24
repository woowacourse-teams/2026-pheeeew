package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.pheeeew.domain.model.MapCameraState
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapCameraActionUiModel
import com.pheeeew.feature.screens.map.MapRenderUiModel

@Composable
internal actual fun NativeMap(
    state: MapRenderUiModel,
    onCameraStateChanged: (MapCameraState) -> Unit,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    modifier: Modifier,
) {
    val currentOnCameraStateChanged by rememberUpdatedState(onCameraStateChanged)
    val currentOnMapError by rememberUpdatedState(onMapError)
    val currentOnMapRecovered by rememberUpdatedState(onMapRecovered)
    val eventSink = remember {
        object : FoundationIosMapEventSink {
            override fun onCameraStateChanged(latitude: Double, longitude: Double, zoom: Double) {
                currentOnCameraStateChanged(MapCameraState(latitude, longitude, zoom))
            }

            override fun onRendererUnavailable() = currentOnMapError(MapErrorUiModel.RendererUnavailable)

            override fun onStyleLoadFailed() = currentOnMapError(MapErrorUiModel.StyleLoadFailed)

            override fun onMapRecovered() = currentOnMapRecovered()
        }
    }

    UIKitView(
        factory = { FoundationIosMapBridge.createMapView(eventSink) },
        modifier = modifier,
        update = { mapView ->
            FoundationIosMapBridge.updateMapView(
                mapView = mapView,
                state = state.toFoundationIosRenderUiModel(),
            )
        },
        onRelease = FoundationIosMapBridge::releaseMapView,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
        ),
    )
}

private fun MapRenderUiModel.toFoundationIosRenderUiModel(): FoundationIosMapRenderUiModel {
    val latitude = currentLocation?.latitude ?: fallbackCameraState.latitude
    val longitude = currentLocation?.longitude ?: fallbackCameraState.longitude
    return FoundationIosMapRenderUiModel(
        currentLocation = currentLocation?.let {
            FoundationIosCurrentLocationUiModel(it.latitude, it.longitude, it.accuracyMeters.toDouble())
        },
        fallbackCenter = FoundationIosMapCoordinateUiModel(latitude, longitude),
        cameraCommandId = cameraCommand?.id ?: 0L,
        cameraCommandType = when (cameraCommand?.action) {
            MapCameraActionUiModel.MoveToCoordinate -> 1
            MapCameraActionUiModel.ZoomBy -> 2
            null -> 0
        },
        cameraLatitude = cameraCommand?.latitude ?: 0.0,
        cameraLongitude = cameraCommand?.longitude ?: 0.0,
        cameraCommandValue = cameraCommand?.value ?: 0.0,
    )
}
