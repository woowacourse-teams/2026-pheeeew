package com.pheeeew.feature.screens.collection

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

internal const val COLLECTION_CARD_SHARE_URL = "https://pheeeew.com"

internal enum class CollectionImageAction { SAVE, SHARE }

internal enum class CollectionImageActionOutcome { SUCCEEDED, CANCELLED, CAPTURE_FAILED, FAILED }

internal data class CollectionImageActionResult(
    val cardId: String,
    val action: CollectionImageAction,
    val outcome: CollectionImageActionOutcome,
)

internal data class CollectionCardImageActions(
    val save: suspend (ByteArray) -> CollectionImageActionOutcome,
    val share: suspend (bytes: ByteArray, url: String) -> CollectionImageActionOutcome,
)

@Composable
internal expect fun rememberCollectionCardImageActions(): CollectionCardImageActions

internal fun ImageBitmap.toPngBytes(): ByteArray {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    val writer = PngByteWriter()
    writer.append(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

    val header = PngByteWriter()
    header.appendInt(width)
    header.appendInt(height)
    header.append(byteArrayOf(8, 6, 0, 0, 0))
    writer.appendChunk("IHDR", header.toByteArray())

    val scanlines = PngByteWriter()
    for (y in 0 until height) {
        scanlines.append(0)
        for (x in 0 until width) {
            val color = pixels[y * width + x]
            scanlines.append((color shr 16).toByte())
            scanlines.append((color shr 8).toByte())
            scanlines.append(color.toByte())
            scanlines.append((color shr 24).toByte())
        }
    }
    writer.appendChunk("IDAT", zlibStored(scanlines.toByteArray()))
    writer.appendChunk("IEND", byteArrayOf())
    return writer.toByteArray()
}

private fun zlibStored(input: ByteArray): ByteArray {
    val writer = PngByteWriter()
    writer.append(0x78)
    writer.append(0x01)
    var offset = 0
    while (offset < input.size) {
        val count = minOf(65_535, input.size - offset)
        writer.append(if (offset + count == input.size) 1 else 0)
        writer.append(count)
        writer.append(count ushr 8)
        val inverted = count.inv() and 0xffff
        writer.append(inverted)
        writer.append(inverted ushr 8)
        writer.append(input, offset, count)
        offset += count
    }
    if (input.isEmpty()) {
        writer.append(1)
        writer.append(0)
        writer.append(0)
        writer.append(0xff)
        writer.append(0xff)
    }
    var a = 1L
    var b = 0L
    input.forEach { value ->
        a = (a + (value.toInt() and 0xff)) % 65_521
        b = (b + a) % 65_521
    }
    writer.appendInt((b shl 16 or a).toInt())
    return writer.toByteArray()
}

private fun PngByteWriter.appendChunk(
    type: String,
    data: ByteArray,
) {
    appendInt(data.size)
    val typeBytes = type.encodeToByteArray()
    append(typeBytes)
    append(data)
    var crc = 0xffff_ffffL
    (typeBytes + data).forEach { value ->
        crc = crc xor (value.toInt() and 0xff).toLong()
        repeat(8) {
            crc = if (crc and 1L == 1L) (crc ushr 1) xor 0xedb8_8320L else crc ushr 1
        }
    }
    appendInt((crc xor 0xffff_ffffL).toInt())
}

private class PngByteWriter(
    initialCapacity: Int = 256,
) {
    private var bytes = ByteArray(initialCapacity)
    private var size = 0

    fun append(value: Int) = append(value.toByte())

    fun append(value: Byte) {
        ensureCapacity(1)
        bytes[size++] = value
    }

    fun append(values: ByteArray) = append(values, 0, values.size)

    fun append(
        values: ByteArray,
        offset: Int,
        count: Int,
    ) {
        ensureCapacity(count)
        values.copyInto(bytes, size, offset, offset + count)
        size += count
    }

    fun appendInt(value: Int) {
        append(value ushr 24)
        append(value ushr 16)
        append(value ushr 8)
        append(value)
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun ensureCapacity(extra: Int) {
        if (size + extra <= bytes.size) return
        var capacity = bytes.size
        while (capacity < size + extra) capacity *= 2
        bytes = bytes.copyOf(capacity)
    }
}
