package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import kotlin.math.roundToInt

/** 플랫폼 객체 없이 HSV와 불투명 ARGB/HEX 사이를 변환합니다. */
internal object ColorConversion {
    fun toSelection(argb: Long): StampColorSelection {
        val red = channel(argb, 16)
        val green = channel(argb, 8)
        val blue = channel(argb, 0)
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val delta = maximum - minimum

        val hue =
            when {
                delta == 0f -> 0f
                maximum == red -> 60f * (((green - blue) / delta) % 6f)
                maximum == green -> 60f * (((blue - red) / delta) + 2f)
                else -> 60f * (((red - green) / delta) + 4f)
            }.let { if (it < 0f) it + 360f else it }

        return StampColorSelection(
            hueDegrees = hue.coerceIn(0f, 359.999f),
            saturation = if (maximum == 0f) 0f else delta / maximum,
            value = maximum,
        )
    }

    fun toArgb(selection: StampColorSelection): Long {
        val hue = normalizeHue(selection.hueDegrees)
        val saturation = selection.saturation.coerceIn(0f, 1f)
        val value = selection.value.coerceIn(0f, 1f)
        val chroma = value * saturation
        val secondary = chroma * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val minimum = value - chroma

        val (red, green, blue) =
            when {
                hue < 60f -> Triple(chroma, secondary, 0f)
                hue < 120f -> Triple(secondary, chroma, 0f)
                hue < 180f -> Triple(0f, chroma, secondary)
                hue < 240f -> Triple(0f, secondary, chroma)
                hue < 300f -> Triple(secondary, 0f, chroma)
                else -> Triple(chroma, 0f, secondary)
            }

        val redByte = ((red + minimum) * 255f).roundToInt().coerceIn(0, 255)
        val greenByte = ((green + minimum) * 255f).roundToInt().coerceIn(0, 255)
        val blueByte = ((blue + minimum) * 255f).roundToInt().coerceIn(0, 255)
        return 0xFF00_0000L or (redByte.toLong() shl 16) or (greenByte.toLong() shl 8) or blueByte.toLong()
    }

    fun toRgbHex(argb: Long): String =
        buildString(7) {
            append('#')
            appendHexByte(this, channelByte(argb, 16))
            appendHexByte(this, channelByte(argb, 8))
            appendHexByte(this, channelByte(argb, 0))
        }

    private fun appendHexByte(target: StringBuilder, value: Int) {
        val digits = "0123456789ABCDEF"
        target.append(digits[value shr 4])
        target.append(digits[value and 0x0F])
    }

    private fun channel(argb: Long, shift: Int): Float = channelByte(argb, shift) / 255f

    private fun channelByte(argb: Long, shift: Int): Int = ((argb shr shift) and 0xFFL).toInt()

    private fun normalizeHue(hue: Float): Float = ((hue % 360f) + 360f) % 360f
}
