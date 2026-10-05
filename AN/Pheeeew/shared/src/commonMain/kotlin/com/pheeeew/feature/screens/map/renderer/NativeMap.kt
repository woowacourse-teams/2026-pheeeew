package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.feature.screens.map.HighlightedPinPosition
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel

data class MapCameraSnapshotUiModel(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val pitch: Double,
    val initialCameraUsedFallback: Boolean,
    val lastAppliedCameraCommandId: Long,
)

@Composable
internal expect fun NativeMap(
    state: MapUiModel,
    isVisible: Boolean,
    isMounted: Boolean,
    savedCamera: MapCameraSnapshotUiModel?,
    onCameraSaved: (MapCameraSnapshotUiModel) -> Unit,
    onMemoryPressure: () -> Unit,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
    onViewportChanged: (EmotionMapBounds) -> Unit,
    onEmotionPinClick: (Long) -> Unit,
    onMapBackgroundClick: () -> Unit,
    onHighlightedPinPositionChanged: (HighlightedPinPosition?) -> Unit,
    onContentPresented: (String, List<String>) -> Unit,
    modifier: Modifier,
)
