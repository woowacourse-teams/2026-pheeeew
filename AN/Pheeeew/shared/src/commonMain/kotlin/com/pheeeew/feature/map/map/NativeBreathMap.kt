package com.pheeeew.feature.map.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.feature.map.MapRenderState

@Composable
internal expect fun NativeBreathMap(
    state: MapRenderState,
    cameraCommand: MapCameraCommand?,
    onSighClick: (String) -> Unit,
    onBoundsChanged: (SighBounds) -> Unit,
    onMapError: (MapError) -> Unit,
    onMapRecovered: () -> Unit,
    onProjectionChanged: (MapProjectionSnapshot) -> Unit,
    modifier: Modifier,
)
