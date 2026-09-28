package com.pheeeew.feature.screens.map

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.component.stamp.StampShapeCatalog
import com.pheeeew.feature.component.stamp.balanceTwoLines
import com.pheeeew.feature.component.stamp.stampFontSize
import com.pheeeew.feature.component.stamp.stampLineHeight
import com.pheeeew.feature.component.stamp.toStampTextLayout
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import kotlinx.coroutines.delay
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
    /** Row-major, unpremultiplied RGBA bytes. */
    val rgba: ByteArray,
)

@Composable
internal fun rememberEmotionPinSymbolImages(pins: List<EmotionPinUiModel>): List<EmotionPinSymbolImage> {
    val appearances = remember(pins) { pins.distinctBy(EmotionPinUiModel::symbolImageKey) }
    val captured = remember { mutableStateMapOf<String, EmotionPinSymbolImage>() }
    val desiredKeys = remember(appearances) { appearances.mapTo(mutableSetOf(), EmotionPinUiModel::symbolImageKey) }
    LaunchedEffect(desiredKeys) { captured.keys.retainAll(desiredKeys) }
    appearances.forEach { pin ->
        key(pin.symbolImageKey()) {
            RasterizePinSymbol(pin) { captured[it.key] = it }
        }
    }
    return appearances.mapNotNull { captured[it.symbolImageKey()] }
}

/** Draws resources directly, independent of AndroidView occlusion and on-screen draw callbacks. */
@Composable
private fun RasterizePinSymbol(
    pin: EmotionPinUiModel,
    onRasterized: (EmotionPinSymbolImage) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val pixels = with(density) { EMOTION_PIN_SIZE.roundToPx() }
    val emotionPainter = painterResource(pin.emotion.icon)
    val stamp = pin.stamp
    val shape = stamp?.let { StampShapeCatalog[it.shape] }
    val backdrop = shape?.let { painterResource(it.backdrop) }
    val fill = shape?.let { painterResource(it.fill) }
    val overlay = shape?.overlay?.let { painterResource(it) }
    val textMeasurer = rememberTextMeasurer()
    val baseTextStyle = LocalTextStyle.current

    LaunchedEffect(
        pin.symbolImageKey(),
        pixels,
        density,
        layoutDirection,
        emotionPainter,
        backdrop,
        fill,
        overlay,
        baseTextStyle,
    ) {
        repeat(10) {
            val bitmap = ImageBitmap(pixels, pixels)
            CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(pixels.toFloat(), pixels.toFloat())) {
                if (stamp == null || shape == null || backdrop == null || fill == null) {
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
                        if (ratio >=
                            1f
                        ) {
                            Size(size.width, size.height / ratio)
                        } else {
                            Size(size.width * ratio, size.height)
                        }
                    translate((size.width - fitted.width) / 2, (size.height - fitted.height) / 2) {
                        with(emotionPainter) { draw(fitted) }
                    }
                } else {
                    val fitted =
                        if (shape.aspectRatio >= 1f) {
                            Size(size.width, size.height / shape.aspectRatio)
                        } else {
                            Size(size.width * shape.aspectRatio, size.height)
                        }
                    translate((size.width - fitted.width) / 2, (size.height - fitted.height) / 2) {
                        with(backdrop) { draw(fitted) }
                        with(fill) { draw(fitted, colorFilter = ColorFilter.tint(Color(stamp.fillArgb.toInt()))) }
                        overlay?.let { with(it) { draw(fitted) } }
                        val area = shape.textArea
                        val layout = stamp.label.toStampTextLayout()
                        val fontSize = stampFontSize(layout, EMOTION_PIN_SIZE, fontScale = density.fontScale)
                        val textStyle =
                            baseTextStyle.copy(
                                color = Color(stamp.textArgb.toInt()),
                                fontSize = fontSize,
                                lineHeight = stampLineHeight(fontSize),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        val textWidth = (fitted.width * area.widthFraction).roundToInt()
                        val maxLines = layout.characterCount.coerceAtLeast(1)
                        val measuredLines =
                            textMeasurer
                                .measure(
                                    layout.text,
                                    style = textStyle,
                                    overflow = TextOverflow.Clip,
                                    maxLines = maxLines,
                                    constraints = Constraints(minWidth = textWidth, maxWidth = textWidth),
                                ).lineCount
                        val text =
                            textMeasurer.measure(
                                layout.balanceTwoLines(measuredLines),
                                style = textStyle,
                                overflow = TextOverflow.Clip,
                                maxLines = maxLines,
                                constraints =
                                    Constraints(
                                        minWidth = textWidth,
                                        maxWidth = textWidth,
                                        maxHeight = (fitted.height * area.heightFraction).roundToInt(),
                                    ),
                            )
                        drawText(
                            text,
                            topLeft =
                                Offset(
                                    fitted.width * (area.centerX - area.widthFraction / 2),
                                    fitted.height * area.centerY - text.size.height / 2f,
                                ),
                        )
                    }
                }
            }
            val image = bitmap.toSymbolImage(pin.symbolImageKey())
            if ((3 until image.rgba.size step 4).any { image.rgba[it].toInt() and 0xff > 0 }) {
                onRasterized(image)
                return@LaunchedEffect
            }
            delay(SYMBOL_RESOURCE_RETRY_MILLIS)
            withFrameNanos { }
        }
    }
}

private fun ImageBitmap.toSymbolImage(key: String): EmotionPinSymbolImage {
    val pixelMap = toPixelMap()
    val rgba = ByteArray(width * height * 4)
    var index = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixelMap[x, y]
            rgba[index++] = (pixel.red * 255).roundToInt().toByte()
            rgba[index++] = (pixel.green * 255).roundToInt().toByte()
            rgba[index++] = (pixel.blue * 255).roundToInt().toByte()
            rgba[index++] = (pixel.alpha * 255).roundToInt().toByte()
        }
    }
    return EmotionPinSymbolImage(
        key = key,
        width = width,
        height = height,
        rgba = rgba,
    )
}

internal fun EmotionPinUiModel.symbolImageKey(): String =
    stamp?.let { appearance ->
        val labelHash = appearance.label.stableImageHash()
        listOf(
            "stamp",
            appearance.shape.name,
            appearance.fillArgb.toString(16),
            appearance.textArgb.toString(16),
            labelHash,
        ).joinToString("-")
    } ?: "emotion-${emotion.name.lowercase()}"

private fun String.stableImageHash(): String {
    var hash = -3_750_761_039_363_611_039L
    encodeToByteArray().forEach { byte ->
        hash = (hash xor (byte.toLong() and 0xff)) * 1_099_511_628_211L
    }
    return hash.toULong().toString(16)
}

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
                stamp = null,
            ),
        )
    val image = rememberEmotionPinSymbolImages(pins).firstOrNull()
    CanvasComposable(
        Modifier.size(80.dp),
    ) {
        if (image != null) {
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
