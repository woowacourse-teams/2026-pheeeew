package com.pheeeew.feature.screens.ranking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.raisedPressEffect
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
internal fun Modifier.rankingCardBorder(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    return this
        .pointerInput(interactionSource) {
            // Visual feedback only: leave scrolling and accessibility semantics unchanged.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                var released = false
                try {
                    if (waitForUpOrCancellation() != null) {
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        released = true
                    }
                } finally {
                    if (!released) interactionSource.tryEmit(PressInteraction.Cancel(press))
                }
            }
        }.drawBehind {
            val shadowOffset = 3.dp.toPx()
            drawRoundRect(
                color = AppColors.GroupInk,
                topLeft = Offset(0f, shadowOffset),
                size = Size(size.width, size.height - shadowOffset),
                cornerRadius = CornerRadius(18.dp.toPx()),
            )
        }.padding(bottom = 3.dp)
        .raisedPressEffect(interactionSource)
        .background(AppColors.Background, shape)
        .border(AppBorders.Standard, AppColors.GroupInk, shape)
}
