package com.pheeeew.legacy.feature.map.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.legacy.domain.model.sigh.SighBounds
import com.pheeeew.legacy.feature.map.MapRenderState

@Composable
internal expect fun NativeBreathMap(
    state: MapRenderState,
    cameraCommand: MapCameraCommand?,
    onSighClick: (String) -> Unit,
    onBoundsChanged: (SighBounds) -> Unit,
    onCameraStateChanged: (MapCameraState) -> Unit,
    onMapError: (MapError) -> Unit,
    onMapRecovered: () -> Unit,
    onProjectionChanged: (MapProjectionSnapshot) -> Unit,
    onVisibleSighsChanged: (List<String>) -> Unit,
    modifier: Modifier,
)
