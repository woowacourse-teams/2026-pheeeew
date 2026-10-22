package com.pheeeew.feature.screens.map

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas as CanvasComposable

internal val EMOTION_PIN_SIZE = 40.dp
private const val SYMBOL_RESOURCE_RETRY_MILLIS = 80L

/** Raster icon registered as a native MapLibre style image. */
data class EmotionPinSymbolImage(
    val key: String,
    val width: Int,
    val height: Int,
    /** Row-major, unpremultiplied RGBA bytes. Empty when Android uses [androidImageBitmap]. */
    val rgba: ByteArray,
    val hasVisiblePixels: Boolean,
    val androidImageBitmap: ImageBitmap? = null,
)

@Composable
internal fun rememberEmotionPinSymbolImages(pins: List<EmotionPinUiModel>): MapSymbolImages {
    val appearances = remember(pins) { pins.distinctBy(EmotionPinUiModel::symbolImageKey) }
    val captured = remember { mutableStateMapOf<String, EmotionPinSymbolImage?>() }
    val desiredKeys = remember(appearances) { appearances.mapTo(mutableSetOf(), EmotionPinUiModel::symbolImageKey) }
    LaunchedEffect(desiredKeys) { captured.keys.retainAll(desiredKeys) }
    appearances.forEach { pin ->
        key(pin.symbolImageKey()) {
            RasterizePinSymbol(pin) { captured[pin.symbolImageKey()] = it }
        }
    }
    return captured.symbolImagesFor(desiredKeys)
}

/** Draws resources directly, independent of AndroidView occlusion and on-screen draw callbacks. */
@Composable
private fun RasterizePinSymbol(
    pin: EmotionPinUiModel,
    onRasterized: (EmotionPinSymbolImage?) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val pixels = with(density) { EMOTION_PIN_SIZE.roundToPx() }
    val emotionPainter = painterResource(pin.emotion.icon)

    LaunchedEffect(
        pin.symbolImageKey(),
        pixels,
        density,
        layoutDirection,
        emotionPainter,
    ) {
        repeat(10) {
            val image =
                withContext(pinSymbolRasterDispatcher()) {
                    val bitmap = ImageBitmap(pixels, pixels)
                    CanvasDrawScope().draw(
                        density,
                        layoutDirection,
                        Canvas(bitmap),
                        Size(pixels.toFloat(), pixels.toFloat()),
                    ) {
                        val intrinsic = emotionPainter.intrinsicSize
                        val ratio =
                            if (intrinsic.width.isFinite() &&
                                intrinsic.height > 0
                            ) {
                                intrinsic.width / intrinsic.height
                            } else {
                                1f
                            }
                        val fitted =
                            if (ratio >= 1f) {
                                Size(size.width, size.height / ratio)
                            } else {
                                Size(size.width * ratio, size.height)
                            }
                        translate((size.width - fitted.width) / 2, (size.height - fitted.height) / 2) {
                            with(emotionPainter) { draw(fitted) }
                        }
                    }
                    bitmap
                        .toEmotionPinSymbolImage(pin.symbolImageKey(), (density.density * 160).roundToInt())
                        .takeIf { it.hasVisiblePixels }
                }
            if (image != null) {
                onRasterized(image)
                return@LaunchedEffect
            }
            delay(SYMBOL_RESOURCE_RETRY_MILLIS)
            withFrameNanos { }
        }
        onRasterized(null)
    }
}

internal fun EmotionPinUiModel.symbolImageKey(): String = "emotion-${emotion.name.lowercase()}"

@Preview(name = "지도 핀 비트맵", widthDp = 120, heightDp = 120, showBackground = true)
@Composable
private fun EmotionPinSymbolImagePreview() {
    val pins =
        listOf(
            EmotionPinUiModel(
                id = 1,
                latitude = 37.4409230460675,
                longitude = 127.147538132656,
                createdAt = "2026-09-27T17:17:25Z",
                rotationDegrees = 0.0,
                emotion = EmotionTypeUiModel.IRRITATED,
            ),
        )
    val image = rememberEmotionPinSymbolImages(pins).images.firstOrNull()
    CanvasComposable(
        Modifier.size(80.dp),
    ) {
        if (image != null) {
            val platformBitmap = image.androidImageBitmap
            if (platformBitmap != null) {
                drawImage(
                    platformBitmap,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            } else {
                val pixelWidth = size.width / image.width
                val pixelHeight = size.height / image.height
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        val index = (y * image.width + x) * 4
                        val alpha = image.rgba[index + 3].toInt() and 0xff
                        if (alpha != 0) {
                            drawRect(
                                color =
                                    Color(
                                        red = (image.rgba[index].toInt() and 0xff) / 255f,
                                        green = (image.rgba[index + 1].toInt() and 0xff) / 255f,
                                        blue = (image.rgba[index + 2].toInt() and 0xff) / 255f,
                                        alpha = alpha / 255f,
                                    ),
                                topLeft = Offset(x * pixelWidth, y * pixelHeight),
                                size = Size(pixelWidth, pixelHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}
