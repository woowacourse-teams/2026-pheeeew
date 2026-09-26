package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal enum class SettingsIcon { Tune, Shield, Document, Info, Mail }

internal object SettingsColors {
    val Ink = Color(0xFF252A2C)
    val IconSurface = Color(0xFFF3F4F4)
    val Secondary = Color(0xFF848B8C)
    val Divider = Color(0xFFE8EAEA)
    val Footer = Color(0xFF9AA0A0)
}

@Composable
internal fun SettingsIconBadge(
    icon: SettingsIcon,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .background(if (highlighted) Color.White else SettingsColors.IconSurface, RoundedCornerShape(12.dp))
                .then(
                    if (highlighted) Modifier.border(1.dp, SettingsColors.Ink, RoundedCornerShape(12.dp)) else Modifier,
                ),
        contentAlignment = Alignment.Center,
    ) {
        SettingsLineIcon(icon, Modifier.size(21.dp))
    }
}

@Composable
internal fun SettingsChevron(modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path =
            Path().apply {
                moveTo(size.width * .40f, size.height * .22f)
                lineTo(size.width * .68f, size.height * .50f)
                lineTo(size.width * .40f, size.height * .78f)
            }
        drawPath(path, SettingsColors.Ink, style = stroke)
    }
}

@Composable
internal fun SettingsLineIcon(
    icon: SettingsIcon,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val color = SettingsColors.Ink
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (icon) {
            SettingsIcon.Tune -> {
                drawLine(
                    color,
                    Offset(w * .22f, h * .30f),
                    Offset(w * .78f, h * .30f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(w * .22f, h * .50f),
                    Offset(w * .78f, h * .50f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(w * .22f, h * .70f),
                    Offset(w * .78f, h * .70f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
                drawCircle(color, w * .075f, Offset(w * .43f, h * .30f))
                drawCircle(color, w * .075f, Offset(w * .63f, h * .50f))
                drawCircle(color, w * .075f, Offset(w * .37f, h * .70f))
            }

            SettingsIcon.Shield -> {
                val path =
                    Path().apply {
                        moveTo(w * .50f, h * .12f)
                        lineTo(w * .82f, h * .25f)
                        lineTo(w * .79f, h * .58f)
                        quadraticTo(w * .73f, h * .79f, w * .50f, h * .90f)
                        quadraticTo(w * .27f, h * .79f, w * .21f, h * .58f)
                        lineTo(w * .18f, h * .25f)
                        close()
                    }
                drawPath(path, color, style = stroke)
                drawLine(
                    color,
                    Offset(w * .36f, h * .51f),
                    Offset(w * .46f, h * .61f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(w * .46f, h * .61f),
                    Offset(w * .66f, h * .40f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            SettingsIcon.Document -> {
                val path =
                    Path().apply {
                        moveTo(w * .27f, h * .12f)
                        lineTo(w * .59f, h * .12f)
                        lineTo(w * .76f, h * .30f)
                        lineTo(w * .76f, h * .86f)
                        lineTo(w * .27f, h * .86f)
                        close()
                        moveTo(w * .58f, h * .13f)
                        lineTo(w * .58f, h * .32f)
                        lineTo(w * .75f, h * .32f)
                    }
                drawPath(path, color, style = stroke)
                drawLine(
                    color,
                    Offset(w * .38f, h * .49f),
                    Offset(w * .65f, h * .49f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(w * .38f, h * .63f),
                    Offset(w * .65f, h * .63f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
            }

            SettingsIcon.Info -> {
                drawCircle(color, w * .36f, Offset(w * .50f, h * .50f), style = stroke)
                drawLine(
                    color,
                    Offset(w * .50f, h * .46f),
                    Offset(w * .50f, h * .68f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
                drawCircle(color, w * .045f, Offset(w * .50f, h * .32f))
            }

            SettingsIcon.Mail -> {
                val left = w * .13f
                val right = w * .87f
                val top = h * .25f
                val bottom = h * .75f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color,
                    Offset(left + w * .04f, top + h * .04f),
                    Offset(w * .50f, h * .54f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(w * .50f, h * .54f),
                    Offset(right - w * .04f, top + h * .04f),
                    stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsIconBadgePreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
        SettingsIconBadge(SettingsIcon.Tune, highlighted = true)
        SettingsIconBadge(SettingsIcon.Shield)
    }
}

@Preview
@Composable
private fun SettingsLineIconPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
        SettingsIcon.entries.forEach { icon -> SettingsLineIcon(icon, Modifier.size(24.dp)) }
    }
}

@Preview
@Composable
private fun SettingsChevronPreview() {
    SettingsChevron(Modifier.padding(8.dp))
}
