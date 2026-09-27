package com.pheeeew.feature.screens.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.component.GroupStamp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private val EMOTION_PIN_SIZE = 40.dp
private const val SYMBOL_IMAGE_CAPTURE_DELAY_MILLIS = 80L

/** Raster icon passed to the native map renderer and registered as a MapLibre style image. */
data class EmotionPinSymbolImage(
    val key: String,
    val width: Int,
    val height: Int,
    /** Row-major, unpremultiplied RGBA bytes. */
    val rgba: ByteArray,
)

private data class PinSymbolAppearance(
    val key: String,
    val pin: EmotionPinUiModel,
)

@Composable
internal fun rememberEmotionPinSymbolImages(pins: List<EmotionPinUiModel>): List<EmotionPinSymbolImage> {
    val appearances =
        remember(pins) {
            pins
                .distinctBy(EmotionPinUiModel::symbolImageKey)
                .map { PinSymbolAppearance(it.symbolImageKey(), it) }
        }
    val captured = remember { mutableStateMapOf<String, EmotionPinSymbolImage>() }
    val desiredKeys = remember(appearances) { appearances.mapTo(mutableSetOf(), PinSymbolAppearance::key) }

    LaunchedEffect(desiredKeys) {
        captured.keys.retainAll(desiredKeys)
    }

    appearances.forEach { appearance ->
        key(appearance.key) {
            CapturePinSymbolImage(
                appearance = appearance,
                onCaptured = { image -> captured[image.key] = image },
            )
        }
    }

    return appearances.mapNotNull { captured[it.key] }
}

@Composable
private fun CapturePinSymbolImage(
    appearance: PinSymbolAppearance,
    onCaptured: (EmotionPinSymbolImage) -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()

    Box(
        modifier =
            Modifier
                .size(EMOTION_PIN_SIZE)
                // Keep this renderer out of the visible map while still composing the exact pin UI.
                .offset(x = (-512).dp, y = (-512).dp)
                .drawWithContent {
                    graphicsLayer.record(
                        density = this,
                        layoutDirection = layoutDirection,
                        size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    ) {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                },
    ) {
        val stamp = appearance.pin.stamp
        if (stamp != null) {
            GroupStamp(appearance = stamp, size = EMOTION_PIN_SIZE)
        } else {
            Image(
                painter = painterResource(appearance.pin.emotion.icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }

    LaunchedEffect(appearance.key, graphicsLayer) {
        // Wait until the first draw has recorded a non-empty layer before reading its pixels.
        while (graphicsLayer.size == IntSize.Zero) {
            withFrameNanos { }
        }
        // Compose resources may resolve asynchronously on iOS; capture after their first draw settles.
        delay(SYMBOL_IMAGE_CAPTURE_DELAY_MILLIS)
        withFrameNanos { }
        onCaptured(graphicsLayer.toImageBitmap().toSymbolImage(appearance.key))
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
