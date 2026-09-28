package com.pheeeew.feature.screens.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Native map projection in density-independent screen coordinates. */
data class HighlightedPinPosition(
    val id: Long,
    val x: Float,
    val y: Float,
)

@Composable
internal fun RegisteredPinHighlight(position: HighlightedPinPosition) {
    val pulse = remember(position.id) { Animatable(0f) }
    val density = LocalDensity.current
    val center = with(density) { Offset(position.x.dp.toPx(), position.y.dp.toPx()) }
    LaunchedEffect(position.id) {
        repeat(2) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = 700, easing = LinearOutSlowInEasing))
        }
    }
    // This overlay only draws, so map gestures and pin taps pass through it.
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
        drawCircle(
            color = Color(0xFF398CFF).copy(alpha = 0.5f * (1f - pulse.value)),
            radius = (24f + 30f * pulse.value).dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        val badgeOffset = (EMOTION_PIN_SIZE / 2 - 3.dp).toPx()
        val badgeCenter = center + Offset(badgeOffset, -badgeOffset)
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = badgeCenter,
        )
        drawCircle(
            color = Color(0xFFFF3B30),
            radius = 5.dp.toPx(),
            center = badgeCenter,
        )
    }
}
