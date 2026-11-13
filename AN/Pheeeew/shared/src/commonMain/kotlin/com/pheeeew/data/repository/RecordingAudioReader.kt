package com.pheeeew.data.repository

/** Reads recording bytes without changing the recording or its ownership. */
fun interface RecordingAudioReader {
    fun read(filePath: String): ByteArray?
}

internal object PlatformRecordingAudioReader : RecordingAudioReader {
    override fun read(filePath: String): ByteArray? = readRecordingAudioFile(filePath)
}
