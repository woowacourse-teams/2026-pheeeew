package com.pheeeew.feature.screens.collection

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.impl.use

internal actual fun ImageBitmap.toPngBytes(): ByteArray {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    val rgba = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { index, color ->
        val offset = index * 4
        rgba[offset] = (color shr 16).toByte()
        rgba[offset + 1] = (color shr 8).toByte()
        rgba[offset + 2] = color.toByte()
        rgba[offset + 3] = (color shr 24).toByte()
    }
    val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    return Bitmap().use { bitmap ->
        check(bitmap.installPixels(imageInfo, rgba, width * 4)) { "카드 픽셀을 준비하지 못했습니다" }
        Image.makeFromBitmap(bitmap).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "카드 PNG를 인코딩하지 못했습니다" }
                .use { it.bytes }
        }
    }
}
