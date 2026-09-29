package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors

/** Share the clickable's interaction source and apply before clipping to keep the shadow fixed. */
@Composable
fun Modifier.raisedButtonBorder(
    shape: Shape,
    interactionSource: InteractionSource,
    color: Color = AppColors.GroupInk,
    elevated: Boolean = true,
): Modifier =
    this
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val shadowOffset = 3.dp.toPx()
            onDrawBehind {
                if (elevated) {
                    translate(top = shadowOffset) {
                        drawOutline(outline, color)
                    }
                }
            }
        }.raisedPressEffect(interactionSource, enabled = elevated)
        .border(AppBorders.Standard, color, shape)
