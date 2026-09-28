package com.pheeeew.data.repository.audio

import java.io.File

internal actual fun readRecordingAudioFile(filePath: String): ByteArray? {
    val file = File(filePath)
    if (!file.isFile || !file.canRead() || file.length() !in 1..MAX_AUDIO_UPLOAD_BYTES) return null
    return runCatching { file.readBytes() }.getOrNull()
}
