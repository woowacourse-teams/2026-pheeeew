package com.pheeeew.core.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.Foundation.NSURL
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
internal suspend fun decodeIosAudioWaveform(url: NSURL): List<Float> {
    val file = AVAudioFile(forReading = url, commonFormat = AVAudioPCMFormatFloat32, interleaved = false, error = null)
    val format = file.processingFormat
    val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = 4096u)
    val waveform = AudioWaveform(file.length, 64)
    while (file.framePosition < file.length) {
        currentCoroutineContext().ensureActive()
        check(file.readIntoBuffer(buffer, error = null))
        val frames = buffer.frameLength.toInt()
        check(frames > 0)
        val samples = checkNotNull(buffer.floatChannelData)
        repeat(frames) { frame ->
            var amplitude = 0f
            repeat(format.channelCount.toInt()) { channel ->
                amplitude = max(amplitude, abs(checkNotNull(samples[channel])[frame]))
            }
            waveform.addFrame(amplitude)
        }
    }
    return waveform.amplitudes()
}
