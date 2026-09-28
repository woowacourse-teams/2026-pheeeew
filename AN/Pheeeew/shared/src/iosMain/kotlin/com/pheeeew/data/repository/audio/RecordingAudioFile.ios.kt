@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pheeeew.data.repository.audio

import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

internal actual fun readRecordingAudioFile(filePath: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(filePath) ?: return null
    if (data.length !in 1uL..MAX_AUDIO_UPLOAD_BYTES.toULong()) return null
    val source = data.bytes?.reinterpret<kotlinx.cinterop.ByteVar>() ?: return null
    return source.readBytes(data.length.toInt())
}
