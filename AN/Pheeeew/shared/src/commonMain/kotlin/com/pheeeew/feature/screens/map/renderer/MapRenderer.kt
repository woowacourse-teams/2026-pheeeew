package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
        isActive: Boolean,
        isMounted: Boolean,
        savedCamera: MapCameraSnapshotUiModel?,
        onCameraSaved: (MapCameraSnapshotUiModel) -> Unit,
        onMemoryPressure: () -> Unit,
        onMapError: (MapErrorUiModel) -> Unit,
        onMapRecovered: () -> Unit,
        onFirstFrameReady: () -> Unit,
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
        isActive: Boolean,
        isMounted: Boolean,
        savedCamera: MapCameraSnapshotUiModel?,
        onCameraSaved: (MapCameraSnapshotUiModel) -> Unit,
        onMemoryPressure: () -> Unit,
        onMapError: (MapErrorUiModel) -> Unit,
        onMapRecovered: () -> Unit,
        onFirstFrameReady: () -> Unit,
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
        val request = remember(provider, state.mapRevision, isMounted) { Any() }
        val styleUrlOverride =
            produceState<StyleUrlOverrideState>(
                initialValue = StyleUrlOverrideState.Loading,
                request,
            ) {
                value =
                    StyleUrlOverrideState.Loaded(
                        request,
                        if (isMounted) provider.consumeForNextLoad() else null,
                    )
            }.value

        val loaded = styleUrlOverride as? StyleUrlOverrideState.Loaded
        // produceState retains its previous value when keys change. Never mount the previous style
        // while the next input is being consumed, or its frame could resolve startup prematurely.
        if (isMounted && loaded?.request !== request) return
        val styleUrl = loaded?.url
        key(styleUrl) {
            NativeMap(
                state = state,
                isVisible = isVisible,
                isActive = isActive,
                isMounted = isMounted,
                savedCamera = savedCamera,
                onCameraSaved = onCameraSaved,
                onMemoryPressure = onMemoryPressure,
                onMapError = onMapError,
                onMapRecovered = onMapRecovered,
                onFirstFrameReady = onFirstFrameReady,
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
        val request: Any,
        val url: String?,
    ) : StyleUrlOverrideState
}
