package com.pheeeew.data.repository.audio

internal const val MAX_AUDIO_UPLOAD_BYTES = 5_242_880L

internal expect fun readRecordingAudioFile(filePath: String): ByteArray?
