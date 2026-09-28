package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.feature.screens.map.MapErrorUiModel
import com.pheeeew.feature.screens.map.MapUiModel

@Composable
internal expect fun NativeMap(
    state: MapUiModel,
    onMapError: (MapErrorUiModel) -> Unit,
    onMapRecovered: () -> Unit,
    onRecordViewportChanged: (centerX: Float, centerY: Float, radius: Float) -> Unit,
    onViewportChanged: (EmotionMapBounds) -> Unit,
    onEmotionPinClick: (Long) -> Unit,
    onContentPresented: (String, List<String>) -> Unit,
    modifier: Modifier,
)
