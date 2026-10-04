package com.pheeeew.feature.screens.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

internal actual fun pinSymbolRasterDispatcher(): CoroutineDispatcher = Dispatchers.Main

internal actual fun ImageBitmap.toEmotionPinSymbolImage(
    key: String,
    @Suppress("UNUSED_PARAMETER") densityDpi: Int,
): EmotionPinSymbolImage {
    val pixelMap = toPixelMap()
    val rgba = ByteArray(width * height * 4)
    var index = 0
    var hasVisiblePixels = false
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixelMap[x, y]
            rgba[index++] = (pixel.red * 255).roundToInt().toByte()
            rgba[index++] = (pixel.green * 255).roundToInt().toByte()
            rgba[index++] = (pixel.blue * 255).roundToInt().toByte()
            val alpha = (pixel.alpha * 255).roundToInt()
            rgba[index++] = alpha.toByte()
            hasVisiblePixels = hasVisiblePixels || alpha > 0
        }
    }
    return EmotionPinSymbolImage(
        key = key,
        width = width,
        height = height,
        rgba = rgba,
        hasVisiblePixels = hasVisiblePixels,
    )
}
