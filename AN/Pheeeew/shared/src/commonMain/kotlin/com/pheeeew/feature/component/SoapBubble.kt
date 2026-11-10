package com.pheeeew.feature.component

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.pheeeew.core.designsystem.theme.AppPalette
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.appSurface

/** Shared iridescent bubble artwork, drawn at any size without bitmap scaling. */
@Composable
internal fun SoapBubble(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors.soapBubble
    val transparent = AppPalette.Transparent
    val white = MaterialTheme.colorScheme.appSurface
    Canvas(modifier) {
        val canvasScale = size.width / 112f
        scale(canvasScale, canvasScale, Offset.Zero) {
            val center = Offset(56f, 56f)
            drawCircle(colors.ambientGlow, 55f, center)
            drawCircle(
                Brush.radialGradient(
                    colors =
                        listOf(
                            colors.radialGlowStart,
                            colors.radialGlowEnd,
                            transparent,
                        ),
                    center = Offset(30f, 24f),
                    radius = 54f,
                ),
                55f,
                center,
            )
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(colors.blueGlow, transparent),
                    center = Offset(25f, 91f),
                    radius = 70f,
                ),
                55f,
                center,
            )
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(colors.pinkGlow, transparent),
                    center = Offset(90f, 76f),
                    radius = 74f,
                ),
                55f,
                center,
            )
            drawCircle(colors.outerRim, 55.3f, center, style = Stroke(0.65f))
            rotate(-70f, center) {
                drawCircle(
                    brush =
                        Brush.sweepGradient(
                            0f to colors.surfaceBase,
                            0.02f to colors.surfaceBase,
                            0.17f to colors.surfaceSky,
                            0.29f to colors.surfaceLavender,
                            0.42f to colors.surfaceRose,
                            0.5f to colors.surfacePeriwinkle,
                            0.6f to colors.surfaceOcean,
                            0.72f to colors.surfaceMint,
                            0.86f to colors.surfaceIce,
                            0.96f to white,
                            1f to white,
                            center = center,
                        ),
                    radius = 54.5f,
                    center = center,
                    alpha = 0.94f,
                    style = Stroke(3f),
                )
            }
            rotate(-17f, center) {
                listOf(
                    colors.polarReflection to 135f,
                    colors.polarReflection to 225f,
                    colors.blueReflection to -45f,
                    colors.faintReflection to 45f,
                ).forEach { (color, startAngle) ->
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(5f, 5f),
                        size = Size(102f, 102f),
                        style = Stroke(1.4f),
                    )
                }
            }
            drawCircle(colors.rimLight, 59f, center, style = Stroke(0.7f))
            rotate(-42f, Offset(31.5f, 15f)) {
                drawOval(white.copy(alpha = 0.92f), Offset(16f, 11f), Size(31f, 8f))
            }
            rotate(15f, Offset(12.5f, 38.5f)) {
                drawOval(white.copy(alpha = 0.75f), Offset(10f, 31f), Size(5f, 15f))
            }
            rotate(-44f, Offset(85.5f, 93.5f)) {
                drawOval(white.copy(alpha = 0.83f), Offset(71f, 91f), Size(29f, 5f))
            }
        }
    }
}
