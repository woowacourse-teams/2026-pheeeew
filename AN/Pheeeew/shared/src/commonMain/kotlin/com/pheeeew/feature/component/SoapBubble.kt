package com.pheeeew.feature.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

/** Shared iridescent bubble artwork, drawn at any size without bitmap scaling. */
@Composable
internal fun SoapBubble(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val canvasScale = size.width / 112f
        scale(canvasScale, canvasScale, Offset.Zero) {
            val center = Offset(56f, 56f)
            drawCircle(Color(0x24EAFBFF), 55f, center)
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0xACFFFFFF), Color(0x48FFFFFF), Color.Transparent),
                    center = Offset(30f, 24f),
                    radius = 54f,
                ),
                55f,
                center,
            )
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0x4495E3F0), Color.Transparent),
                    center = Offset(25f, 91f),
                    radius = 70f,
                ),
                55f,
                center,
            )
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0x30FFB8E3), Color.Transparent),
                    center = Offset(90f, 76f),
                    radius = 74f,
                ),
                55f,
                center,
            )
            drawCircle(Color(0x537699A1), 55.3f, center, style = Stroke(0.65f))
            rotate(-70f, center) {
                drawCircle(
                    brush =
                        Brush.sweepGradient(
                            0f to Color(0xFFF9FFFF),
                            0.02f to Color(0xFFF9FFFF),
                            0.17f to Color(0xFFA7D8F5),
                            0.29f to Color(0xFFD6CBFF),
                            0.42f to Color(0xFFFFDDDD),
                            0.5f to Color(0xFFD9D9FF),
                            0.6f to Color(0xFFA5DDEF),
                            0.72f to Color(0xFFE4FFDD),
                            0.86f to Color(0xFFD8FBFF),
                            0.96f to Color.White,
                            1f to Color.White,
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
                    Color(0x99FFFFFF) to 135f,
                    Color(0x99FFFFFF) to 225f,
                    Color(0x4DC0CEFF) to -45f,
                    Color(0x12FFFFFF) to 45f,
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
            drawCircle(Color(0x85EFFAFF), 59f, center, style = Stroke(0.7f))
            rotate(-42f, Offset(31.5f, 15f)) {
                drawOval(Color.White.copy(alpha = 0.92f), Offset(16f, 11f), Size(31f, 8f))
            }
            rotate(15f, Offset(12.5f, 38.5f)) {
                drawOval(Color.White.copy(alpha = 0.75f), Offset(10f, 31f), Size(5f, 15f))
            }
            rotate(-44f, Offset(85.5f, 93.5f)) {
                drawOval(Color.White.copy(alpha = 0.83f), Offset(71f, 91f), Size(29f, 5f))
            }
        }
    }
}
