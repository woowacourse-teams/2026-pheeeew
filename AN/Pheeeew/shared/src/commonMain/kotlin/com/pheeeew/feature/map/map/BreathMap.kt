package com.pheeeew.feature.map.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.feature.map.MapRenderState

@Composable
fun BreathMap(
    state: MapRenderState,
    cameraCommand: MapCameraCommand?,
    onSighClick: (String) -> Unit,
    onBoundsChanged: (SighBounds) -> Unit,
    onCameraStateChanged: (MapCameraState) -> Unit,
    onMapError: (MapError) -> Unit,
    onMapRecovered: () -> Unit,
    onProjectionChanged: (MapProjectionSnapshot) -> Unit,
    onVisibleSighsChanged: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NativeBreathMap(
        state = state,
        cameraCommand = cameraCommand,
        onSighClick = onSighClick,
        onBoundsChanged = onBoundsChanged,
        onCameraStateChanged = onCameraStateChanged,
        onMapError = onMapError,
        onMapRecovered = onMapRecovered,
        onProjectionChanged = onProjectionChanged,
        onVisibleSighsChanged = onVisibleSighsChanged,
        modifier = modifier,
    )
}
