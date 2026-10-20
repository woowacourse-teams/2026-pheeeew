package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapViewport
import com.pheeeew.feature.screens.map.HighlightedPinPosition
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel

/** A root UI can select the renderer without changing feature state or platform code. */
interface MapRenderer {
    @Composable
    fun Content(
        state: MapUiModel,
        isVisible: Boolean,
        isMounted: Boolean,
        savedCamera: MapCameraSnapshotUiModel?,
        onCameraSaved: (MapCameraSnapshotUiModel) -> Unit,
        onMemoryPressure: () -> Unit,
        onMapError: (MapErrorUiModel) -> Unit,
        onMapRecovered: () -> Unit,
        onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
        onViewportChanged: (EmotionMapViewport) -> Unit,
        onCameraMovingChanged: (Boolean) -> Unit,
        onEmotionPinClick: (Long) -> Unit,
        onRegionClusterClick: (String) -> Unit,
        onMapBackgroundClick: () -> Unit,
        onHighlightedPinPositionChanged: (HighlightedPinPosition?) -> Unit,
        onContentPresented: (String, List<String>) -> Unit,
        modifier: Modifier,
    )
}

val LocalMapRenderer = staticCompositionLocalOf<MapRenderer> { NativeMapRenderer }

object NativeMapRenderer : MapRenderer {
    @Composable
    override fun Content(
        state: MapUiModel,
        isVisible: Boolean,
        isMounted: Boolean,
        savedCamera: MapCameraSnapshotUiModel?,
        onCameraSaved: (MapCameraSnapshotUiModel) -> Unit,
        onMemoryPressure: () -> Unit,
        onMapError: (MapErrorUiModel) -> Unit,
        onMapRecovered: () -> Unit,
        onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
        onViewportChanged: (EmotionMapViewport) -> Unit,
        onCameraMovingChanged: (Boolean) -> Unit,
        onEmotionPinClick: (Long) -> Unit,
        onRegionClusterClick: (String) -> Unit,
        onMapBackgroundClick: () -> Unit,
        onHighlightedPinPositionChanged: (HighlightedPinPosition?) -> Unit,
        onContentPresented: (String, List<String>) -> Unit,
        modifier: Modifier,
    ) {
        val provider = LocalMapStyleOverrideProvider.current
        val styleUrlOverride =
            produceState<StyleUrlOverrideState>(
                initialValue = StyleUrlOverrideState.Loading,
                provider,
                state.mapRevision,
                isMounted,
            ) {
                value =
                    StyleUrlOverrideState.Loaded(
                        if (isMounted) provider.consumeForNextLoad() else null,
                    )
            }.value

        if (isMounted && styleUrlOverride == StyleUrlOverrideState.Loading) return
        val styleUrl = (styleUrlOverride as? StyleUrlOverrideState.Loaded)?.url
        key(styleUrl) {
            NativeMap(
                state = state,
                isVisible = isVisible,
                isMounted = isMounted,
                savedCamera = savedCamera,
                onCameraSaved = onCameraSaved,
                onMemoryPressure = onMemoryPressure,
                onMapError = onMapError,
                onMapRecovered = onMapRecovered,
                onRecordViewportChanged = onRecordViewportChanged,
                onViewportChanged = onViewportChanged,
                onCameraMovingChanged = onCameraMovingChanged,
                onEmotionPinClick = onEmotionPinClick,
                onRegionClusterClick = onRegionClusterClick,
                onMapBackgroundClick = onMapBackgroundClick,
                onHighlightedPinPositionChanged = onHighlightedPinPositionChanged,
                onContentPresented = onContentPresented,
                styleUrlOverride = styleUrl,
                modifier = modifier,
            )
        }
    }
}

private sealed interface StyleUrlOverrideState {
    data object Loading : StyleUrlOverrideState

    data class Loaded(
        val url: String?,
    ) : StyleUrlOverrideState
}
