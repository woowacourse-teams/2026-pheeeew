package com.pheeeew.feature.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.pheeeew.core.designsystem.theme.AppColors

/** Shared iridescent bubble artwork, drawn at any size without bitmap scaling. */
@Composable
internal fun SoapBubble(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val canvasScale = size.width / 112f
        scale(canvasScale, canvasScale, Offset.Zero) {
            val center = Offset(56f, 56f)
            drawCircle(AppColors.SoapBubble.AmbientGlow, 55f, center)
            drawCircle(
                Brush.radialGradient(
                    colors =
                        listOf(
                            AppColors.SoapBubble.RadialGlowStart,
                            AppColors.SoapBubble.RadialGlowEnd,
                            AppColors.Transparent,
                        ),
                    center = Offset(30f, 24f),
                    radius = 54f,
                ),
                55f,
                center,
            )
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(AppColors.SoapBubble.BlueGlow, AppColors.Transparent),
                    center = Offset(25f, 91f),
                    radius = 70f,
                ),
                55f,
                center,
            )
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(AppColors.SoapBubble.PinkGlow, AppColors.Transparent),
                    center = Offset(90f, 76f),
                    radius = 74f,
                ),
                55f,
                center,
            )
            drawCircle(AppColors.SoapBubble.OuterRim, 55.3f, center, style = Stroke(0.65f))
            rotate(-70f, center) {
                drawCircle(
                    brush =
                        Brush.sweepGradient(
                            0f to AppColors.SoapBubble.SurfaceBase,
                            0.02f to AppColors.SoapBubble.SurfaceBase,
                            0.17f to AppColors.SoapBubble.SurfaceSky,
                            0.29f to AppColors.SoapBubble.SurfaceLavender,
                            0.42f to AppColors.SoapBubble.SurfaceRose,
                            0.5f to AppColors.SoapBubble.SurfacePeriwinkle,
                            0.6f to AppColors.SoapBubble.SurfaceOcean,
                            0.72f to AppColors.SoapBubble.SurfaceMint,
                            0.86f to AppColors.SoapBubble.SurfaceIce,
                            0.96f to AppColors.White,
                            1f to AppColors.White,
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
                    AppColors.SoapBubble.PolarReflection to 135f,
                    AppColors.SoapBubble.PolarReflection to 225f,
                    AppColors.SoapBubble.BlueReflection to -45f,
                    AppColors.SoapBubble.FaintReflection to 45f,
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
            drawCircle(AppColors.SoapBubble.RimLight, 59f, center, style = Stroke(0.7f))
            rotate(-42f, Offset(31.5f, 15f)) {
                drawOval(AppColors.White.copy(alpha = 0.92f), Offset(16f, 11f), Size(31f, 8f))
            }
            rotate(15f, Offset(12.5f, 38.5f)) {
                drawOval(AppColors.White.copy(alpha = 0.75f), Offset(10f, 31f), Size(5f, 15f))
            }
            rotate(-44f, Offset(85.5f, 93.5f)) {
                drawOval(AppColors.White.copy(alpha = 0.83f), Offset(71f, 91f), Size(29f, 5f))
            }
        }
    }
}
