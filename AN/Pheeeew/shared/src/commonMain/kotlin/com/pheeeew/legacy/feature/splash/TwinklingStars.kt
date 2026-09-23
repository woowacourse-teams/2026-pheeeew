package com.pheeeew.legacy.feature.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun TwinklingStars(modifier: Modifier = Modifier) {
    val stars = remember { generateStars(STAR_COUNT) }
    val infiniteTransition = rememberInfiniteTransition(label = "starTwinkle")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = STAR_CYCLE_MILLIS, easing = LinearEasing),
            ),
        label = "starTime",
    )

    Canvas(modifier = modifier.fillMaxWidth().fillMaxHeight(STAR_AREA_HEIGHT_FRACTION)) {
        stars.forEach { star ->
            val wave = (sin(time * star.speed + star.phaseOffset) + 1f) / 2f
            val sharpened = wave * wave * wave
            val brightness = if (star.shape == StarShape.Circle) STAR_CIRCLE_BRIGHTNESS else 1f
            val alpha = (STAR_MIN_ALPHA + sharpened * (1f - STAR_MIN_ALPHA)) * brightness
            val radiusPx = star.radius.toPx() * (STAR_MIN_SCALE + sharpened * (1f - STAR_MIN_SCALE))
            val color = AppColors.Cream100.copy(alpha = alpha)
            val center = Offset(size.width * star.xFraction, size.height * star.yFraction)
            when (val shape = star.shape) {
                StarShape.Circle -> {
                    drawCircle(color = color, radius = radiusPx, center = center)
                }

                is StarShape.Sparkle -> {
                    drawSparkle(
                        path = shape.path,
                        center = center,
                        outerRadius = radiusPx,
                        color = color,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSparkle(
    path: Path,
    center: Offset,
    outerRadius: Float,
    color: Color,
) {
    val innerRadius = outerRadius * STAR_SPARKLE_INNER_RATIO
    path.reset()
    for (i in 0 until STAR_SPARKLE_POINTS * 2) {
        val angle = (PI.toFloat() / STAR_SPARKLE_POINTS) * i - (PI.toFloat() / 2f)
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}

private sealed interface StarShape {
    data object Circle : StarShape

    data class Sparkle(
        val path: Path,
    ) : StarShape
}

private data class Star(
    val xFraction: Float,
    val yFraction: Float,
    val radius: Dp,
    val phaseOffset: Float,
    val speed: Float,
    val shape: StarShape,
)

private fun generateStars(count: Int): List<Star> {
    val random = Random(STAR_SEED)
    return List(count) { index ->
        val shape = if (index < STAR_SPARKLE_COUNT) StarShape.Sparkle(Path()) else StarShape.Circle
        val radius =
            when (shape) {
                is StarShape.Sparkle -> (random.nextFloat() * 2f + 3f).dp
                StarShape.Circle -> (random.nextFloat() * 1.4f + 0.8f).dp
            }
        Star(
            xFraction = random.nextFloat(),
            yFraction = random.nextFloat().pow(STAR_Y_BIAS_EXPONENT),
            radius = radius,
            phaseOffset = random.nextFloat() * (2 * PI).toFloat(),
            speed = random.nextFloat() * 1.8f + 0.5f,
            shape = shape,
        )
    }
}

private const val STAR_COUNT = 60
private const val STAR_CYCLE_MILLIS = 6000
private const val STAR_MIN_ALPHA = 0.05f
private const val STAR_MIN_SCALE = 0.6f
private const val STAR_SEED = 20260901L
private const val STAR_AREA_HEIGHT_FRACTION = 4f / 5f
private const val STAR_Y_BIAS_EXPONENT = 1.6f
private const val STAR_SPARKLE_COUNT = 15
private const val STAR_SPARKLE_POINTS = 4
private const val STAR_SPARKLE_INNER_RATIO = 0.35f
private const val STAR_CIRCLE_BRIGHTNESS = 0.6f
