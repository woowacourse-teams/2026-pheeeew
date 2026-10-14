package com.pheeeew.feature.screens.map

import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private val pinRasterDispatcher = Dispatchers.Default.limitedParallelism(2)

internal actual fun pinSymbolRasterDispatcher(): CoroutineDispatcher = pinRasterDispatcher

internal actual fun ImageBitmap.toEmotionPinSymbolImage(
    key: String,
    densityDpi: Int,
): EmotionPinSymbolImage {
    val bitmap = asAndroidBitmap().apply { density = densityDpi }
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return EmotionPinSymbolImage(
        key = key,
        width = width,
        height = height,
        rgba = ByteArray(0),
        hasVisiblePixels = pixels.any { Color.alpha(it) > 0 },
        androidImageBitmap = this,
    )
}
