package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapViewport
import com.pheeeew.feature.screens.map.HighlightedPinPosition
import com.pheeeew.feature.screens.map.MapCameraActionUiModel
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel
import com.pheeeew.feature.screens.map.symbolImageKey

private const val FALLBACK_LATITUDE = 37.4409230460675
private const val FALLBACK_LONGITUDE = 127.147538132656

@Composable
internal actual fun NativeMap(
    state: MapUiModel,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
    onViewportChanged: (EmotionMapViewport) -> Unit,
    onEmotionPinClick: (Long) -> Unit,
    onRegionClusterClick: (String) -> Unit,
    onMapBackgroundClick: () -> Unit,
    onHighlightedPinPositionChanged: (HighlightedPinPosition?) -> Unit,
    onContentPresented: (String, List<String>) -> Unit,
    modifier: Modifier,
) {
    val currentOnHighlightPosition by rememberUpdatedState(onHighlightedPinPositionChanged)
    val currentOnContentPresented by rememberUpdatedState(onContentPresented)
    val currentOnEmotionPinClick by rememberUpdatedState(onEmotionPinClick)
    val currentOnRegionClusterClick by rememberUpdatedState(onRegionClusterClick)
    val currentOnMapBackgroundClick by rememberUpdatedState(onMapBackgroundClick)
    val currentOnMapError by rememberUpdatedState(onMapError)
    val currentOnMapRecovered by rememberUpdatedState(onMapRecovered)
    val currentOnRecordViewportChanged by rememberUpdatedState(onRecordViewportChanged)
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val eventSink =
        remember {
            object : FoundationIosMapEventSink {
                override fun onHighlightedPinPositionChanged(position: HighlightedPinPosition?) {
                    currentOnHighlightPosition(position)
                }

                override fun onContentPresented(
                    loadId: String,
                    entryIds: List<String>,
                ) = currentOnContentPresented(loadId, entryIds)

                override fun onEmotionPinClick(id: Long) = currentOnEmotionPinClick(id)

                override fun onRegionClusterClick(id: String) = currentOnRegionClusterClick(id)

                override fun onMapBackgroundClick() = currentOnMapBackgroundClick()

                override fun onRendererUnavailable() = currentOnMapError(MapErrorUiModel.RendererUnavailable)

                override fun onStyleLoadFailed() = currentOnMapError(MapErrorUiModel.StyleLoadFailed)

                override fun onMapRecovered() = currentOnMapRecovered()

                override fun onRecordViewportChanged(
                    centerX: Float,
                    centerY: Float,
                    radius: Float,
                ) {
                    currentOnRecordViewportChanged(centerX, centerY, radius)
                }

                override fun onViewportChanged(viewport: EmotionMapViewport) {
                    currentOnViewportChanged(viewport)
                }
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
        properties =
            UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = false,
            ),
    )
}

private fun MapUiModel.toFoundationIosRenderUiModel(): FoundationIosMapRenderUiModel {
    val currentLocation = (locationState as? LocationState.Available)?.location
    val latitude = currentLocation?.latitude ?: FALLBACK_LATITUDE
    val longitude = currentLocation?.longitude ?: FALLBACK_LONGITUDE
    return FoundationIosMapRenderUiModel(
        monitoringLoadId = emotionContentLoad?.loadId,
        currentLocation =
            currentLocation?.let {
                FoundationIosCurrentLocationUiModel(it.latitude, it.longitude, it.accuracyMeters.toDouble())
            },
        fallbackCenter = FoundationIosMapCoordinateUiModel(latitude, longitude),
        cameraCommandId = cameraCommand?.id ?: 0L,
        cameraCommandType =
            when (cameraCommand?.action) {
                MapCameraActionUiModel.MoveToCoordinate -> 1
                MapCameraActionUiModel.ZoomBy -> 2
                null -> 0
            },
        cameraLatitude = cameraCommand?.latitude ?: 0.0,
        cameraLongitude = cameraCommand?.longitude ?: 0.0,
        cameraCommandValue = cameraCommand?.value ?: 0.0,
        cameraVerticalPosition = cameraCommand?.verticalPosition ?: 0.5,
        isRecordLocationPicking = isRecordLocationPicking,
        recordPreviewScale = recordPreviewScale,
        recordPreviewPin =
            recordPreviewPin?.let {
                FoundationIosEmotionPinCoordinateUiModel(it.id, it.latitude, it.longitude, it.symbolImageKey(), 0.0)
            },
        recordOrigin = recordOrigin?.let { FoundationIosMapCoordinateUiModel(it.latitude, it.longitude) },
        highlightedEmotionId = highlightedEmotionId,
        pressedEmotionId = pressedEmotionId,
        pressedEmotionScale = pressedEmotionScale,
        focusedEmotionId = focusedEmotionId,
        emotionPinCoordinates =
            emotionPins.map {
                FoundationIosEmotionPinCoordinateUiModel(
                    id = it.id,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    imageKey = it.symbolImageKey(),
                    rotationDegrees = it.rotationDegrees,
                )
            },
        regionClusterCoordinates = regionClusters.map {
            FoundationIosRegionClusterCoordinateUiModel(it.id, it.latitude, it.longitude, it.symbolImageKey())
        },
        emotionPinSymbolImages =
            (emotionPinSymbolImages + regionClusterSymbolImages).distinctBy { it.key }.map {
                FoundationIosMapSymbolImageUiModel(it.key, it.width, it.height, it.rgba)
            },
    )
}
