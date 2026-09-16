package com.pheeeew.feature.map.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI

@Composable
fun StarFlightOverlay(
    flight: SighFlight,
    onLanded: (String) -> Unit,
    onCancelled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember(flight.id) { mutableStateOf(0f) }
    var animationCompleted by remember(flight.id) { mutableStateOf(false) }
    DisposableEffect(flight.id) {
        onDispose {
            if (!animationCompleted) onCancelled(flight.id)
        }
    }
    LaunchedEffect(flight.id) {
        val animation = Animatable(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 480f),
        ) { progress = value }
        animationCompleted = true
        onLanded(flight.id)
    }
    Canvas(modifier) {
        val point = cubicPoint(flight.origin, flight.destination, progress)
        val trailStart = (progress - 0.18f).coerceAtLeast(0f)
        repeat(6) { index ->
            val trailProgress = trailStart + (progress - trailStart) * index / 6f
            val trail = cubicPoint(flight.origin, flight.destination, trailProgress)
            drawCircle(Color.White.copy(alpha = (1f - index / 6f) * 0.28f), (2.5f - index * 0.25f).dp.toPx(), trail)
        }
        drawCircle(
            brush =
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.8f), Color.Transparent),
                    radius = 24.dp.toPx(),
                ),
            radius = 24.dp.toPx(),
            center = point,
        )
        drawStar(point, 11.dp.toPx())
    }
}

private fun cubicPoint(
    origin: Offset,
    destination: Offset,
    progress: Float,
): Offset {
    val control =
        Offset(
            x = (origin.x + destination.x) / 2f,
            y = minOf(origin.y, destination.y) - 96f,
        )
    val inverse = 1f - progress
    return Offset(
        inverse * inverse * origin.x + 2f * inverse * progress * control.x + progress * progress * destination.x,
        inverse * inverse * origin.y + 2f * inverse * progress * control.y + progress * progress * destination.y,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
) {
    val path = Path()
    repeat(10) { index ->
        val angle = -PI.toFloat() / 2f + index * PI.toFloat() / 5f
        val currentRadius = if (index % 2 == 0) radius else radius * 0.42f
        val point =
            Offset(
                center.x + kotlin.math.cos(angle) * currentRadius,
                center.y + kotlin.math.sin(angle) * currentRadius,
            )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, Color(0xFFFFE4A8))
}
