package com.pheeeew.feature.screens.map

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pheeeew.feature.screens.map.renderer.FoundationIosMapSymbolImageUiModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.getBytes
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class PinSymbolPixelTransferTest {
    @Test
    fun packedPixelsPreserveTransparentAndTranslucentColors() {
        val bitmap = ImageBitmap(4, 1)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(4f, 1f)) {
            drawRect(Color(0xFF123456), size = Size(1f, 1f))
            drawRect(
                Color(0x804080C0),
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(1f, 0f),
                size = Size(1f, 1f),
            )
            drawRect(
                Color(0x0100FF80),
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(2f, 0f),
                size = Size(1f, 1f),
            )
        }
        val reference = bitmap.toPixelMap()
        val expected = ByteArray(16)
        for (x in 0 until 4) {
            val pixel = reference[x, 0]
            expected[x * 4] = (pixel.red * 255).roundToInt().toByte()
            expected[x * 4 + 1] = (pixel.green * 255).roundToInt().toByte()
            expected[x * 4 + 2] = (pixel.blue * 255).roundToInt().toByte()
            expected[x * 4 + 3] = (pixel.alpha * 255).roundToInt().toByte()
        }
        val image = bitmap.toEmotionPinSymbolImage("test", 160)
        assertContentEquals(expected, image.rgba)
        assertTrue(image.hasVisiblePixels)
    }

    @Test
    fun bulkCopyOwnsBytesAfterInputChanges() {
        val rgba = byteArrayOf(0, 127, -128, -1)
        val image = FoundationIosMapSymbolImageUiModel("test", 1, 1, rgba)
        val copied = image.copyRgbaData()
        rgba.fill(0)
        val actual = ByteArray(4)
        actual.usePinned { copied.getBytes(it.addressOf(0), 4uL) }
        assertContentEquals(byteArrayOf(0, 127, -128, -1), actual)
        assertEquals(0uL, FoundationIosMapSymbolImageUiModel("empty", 0, 0, byteArrayOf()).copyRgbaData().length)
    }
}
