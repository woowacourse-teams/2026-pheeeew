package com.pheeeew.core.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

internal suspend fun decodeAndroidAudioWaveform(path: String): List<Float> =
    withTimeout(30_000) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            val track =
                (0 until extractor.trackCount).first { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val waveform = AudioWaveform(durationUs * sampleRate / 1_000_000, 64)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = checkNotNull(decoder.getInputBuffer(inputIndex))
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = decoder.outputFormat
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding =
                            if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                    }

                    else -> {
                        if (outputIndex >= 0) {
                            try {
                                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    val buffer =
                                        checkNotNull(
                                            decoder.getOutputBuffer(outputIndex),
                                        ).order(ByteOrder.LITTLE_ENDIAN)
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    val bytesPerSample =
                                        when (encoding) {
                                            AudioFormat.ENCODING_PCM_16BIT -> 2
                                            AudioFormat.ENCODING_PCM_FLOAT -> 4
                                            else -> error("Unsupported decoded PCM encoding")
                                        }
                                    while (buffer.remaining() >= channels * bytesPerSample) {
                                        var amplitude = 0f
                                        repeat(channels) {
                                            val sample =
                                                if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                                    buffer.float
                                                } else {
                                                    buffer.short / 32768f
                                                }
                                            amplitude = max(amplitude, abs(sample))
                                        }
                                        waveform.addFrame(amplitude)
                                    }
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            } finally {
                                decoder.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                }
            }
            waveform.amplitudes()
        } finally {
            codec?.let { decoder ->
                runCatching { decoder.stop() }
                decoder.release()
            }
            extractor.release()
        }
    }
