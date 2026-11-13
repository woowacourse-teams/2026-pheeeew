package com.pheeeew.feature.screens.collection

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionCardPngEncoderTest {
    @Test
    fun compressedPngPreservesDimensionsColorAndTransparency() {
        val color = Color(0x8042A5F5)
        val bitmap = ImageBitmap(64, 64)
        Canvas(bitmap).drawRect(
            left = 0f,
            top = 0f,
            right = 64f,
            bottom = 64f,
            paint = Paint().apply { this.color = color },
        )

        val png = bitmap.toPngBytes()

        assertTrue(png.size < 64 * 64 * 4, "단색 카드는 압축된 PNG로 저장되어야 합니다")
        assertTrue(png.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)))
        val decoded = png.decodeToImageBitmap()
        assertEquals(64, decoded.width)
        assertEquals(64, decoded.height)
        val pixel = IntArray(1)
        decoded.readPixels(pixel, startX = 0, startY = 0, width = 1, height = 1)
        assertEquals(color.toArgb(), pixel[0])
    }
}
