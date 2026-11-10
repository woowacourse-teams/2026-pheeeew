package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.appInk

/** Share the clickable's interaction source and apply before clipping to keep the shadow fixed. */
@Composable
fun Modifier.raisedButtonBorder(
    shape: Shape,
    interactionSource: InteractionSource,
    color: Color? = null,
    elevated: Boolean = true,
): Modifier {
    val borderColor = color ?: MaterialTheme.colorScheme.appInk
    val raisedBorder = if (elevated) raisedBorderBase(shape, borderColor, depth = 3.dp) else this
    return raisedBorder
        .raisedPressEffect(interactionSource, enabled = elevated)
        .border(AppBorders.Standard, borderColor, shape)
}

internal fun Modifier.raisedBorderBase(
    shape: Shape,
    color: Color,
    depth: Dp,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val offset = depth.toPx()
        onDrawBehind {
            translate(top = offset) {
                drawOutline(outline, color)
            }
        }
    }
