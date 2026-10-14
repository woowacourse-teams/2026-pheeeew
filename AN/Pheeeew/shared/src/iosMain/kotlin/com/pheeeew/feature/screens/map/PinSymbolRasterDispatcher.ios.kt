package com.pheeeew.feature.screens.map

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun pinSymbolRasterDispatcher(): CoroutineDispatcher = Dispatchers.Main

internal actual fun ImageBitmap.toEmotionPinSymbolImage(
    key: String,
    @Suppress("UNUSED_PARAMETER") densityDpi: Int,
): EmotionPinSymbolImage {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    val rgba = ByteArray(width * height * 4)
    var index = 0
    var hasVisiblePixels = false
    for (pixel in pixels) {
        rgba[index++] = (pixel ushr 16).toByte()
        rgba[index++] = (pixel ushr 8).toByte()
        rgba[index++] = pixel.toByte()
        val alpha = pixel ushr 24
        rgba[index++] = alpha.toByte()
        hasVisiblePixels = hasVisiblePixels || alpha > 0
    }
    return EmotionPinSymbolImage(
        key = key,
        width = width,
        height = height,
        rgba = rgba,
        hasVisiblePixels = hasVisiblePixels,
    )
}
