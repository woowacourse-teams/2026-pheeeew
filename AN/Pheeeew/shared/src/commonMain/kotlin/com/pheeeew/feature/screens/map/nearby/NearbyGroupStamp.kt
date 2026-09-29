package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel

/** Scale the complete editor composition so small stamps retain all of their lettering. */
@Composable
internal fun NearbyGroupStamp(
    appearance: StampAppearanceUiModel,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val editorSize = 96.dp
    val scale = size / editorSize
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        GroupStamp(
            appearance = appearance,
            size = editorSize,
            modifier =
                Modifier.requiredSize(editorSize).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
