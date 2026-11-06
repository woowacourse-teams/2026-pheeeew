package com.pheeeew.feature.screens.press.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors

/** The halo breathes without changing layout or repeatedly announcing activity to accessibility services. */
@Composable
internal fun PressActivityLiveIndicator(
    active: Boolean,
    pulseEnabled: Boolean,
) {
    val scale =
        if (active && pulseEnabled) {
            val transition = rememberInfiniteTransition(label = "press activity live")
            val animatedScale by transition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.25f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "activity halo scale",
            )
            animatedScale
        } else {
            1f
        }
    Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (active && pulseEnabled) 1.3f - scale * 0.5f else 1f
                }.background(
                    if (active) {
                        AppColors.Press.LiveIndicator.ActiveSurface
                    } else {
                        AppColors.Press.LiveIndicator.InactiveSurface
                    },
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(
                    6.dp,
                ).background(
                    if (active) AppColors.Press.LiveIndicator.Active else AppColors.Press.LiveIndicator.Inactive,
                    CircleShape,
                ),
        )
    }
}
