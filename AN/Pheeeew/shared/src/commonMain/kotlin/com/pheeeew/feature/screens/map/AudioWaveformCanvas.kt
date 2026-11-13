package com.pheeeew.feature.screens.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawPlaybackWaveform(
    waveform: List<Float>,
    progress: Float,
    inactiveColor: Color,
    activeColor: Color,
) {
    drawWaveformBars(waveform, inactiveColor)
    if (progress > 0f) {
        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
            drawWaveformBars(waveform, activeColor)
        }
    }
}

private fun DrawScope.drawWaveformBars(
    waveform: List<Float>,
    color: Color,
) {
    if (waveform.isEmpty()) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), StrokeCap.Round)
        return
    }
    val bars = (size.width / 6.dp.toPx()).toInt().coerceIn(1, waveform.size)
    val step = size.width / bars
    repeat(bars) { index ->
        val start = index * waveform.size / bars
        val end = (index + 1) * waveform.size / bars
        val amplitude = waveform.subList(start, end).maxOrNull() ?: 0f
        val height = (size.height * amplitude.coerceIn(0f, 1f)).coerceAtLeast(3.dp.toPx())
        val x = step * (index + .5f)
        drawLine(
            color,
            Offset(x, (size.height - height) / 2),
            Offset(x, (size.height + height) / 2),
            3.dp.toPx(),
            StrokeCap.Round,
        )
    }
}
