package com.pheeeew.core.audio

import kotlin.math.abs
import kotlin.math.sqrt

/** Bounded-memory RMS envelope, with equally spaced bins covering the whole file. */
internal class AudioWaveform(
    totalFrames: Long,
    private val binCount: Int,
) {
    private val frameCount = totalFrames.coerceAtLeast(1)
    private val energy = DoubleArray(binCount)
    private val counts = LongArray(binCount)
    private var frame = 0L

    init {
        require(binCount > 0)
    }

    fun addFrame(amplitude: Float) {
        val index = ((frame * binCount) / frameCount).coerceIn(0, (binCount - 1).toLong()).toInt()
        val sample = if (amplitude.isFinite()) abs(amplitude).coerceIn(0f, 1f).toDouble() else 0.0
        energy[index] += sample * sample
        counts[index]++
        frame++
    }

    fun amplitudes(): List<Float> {
        val rms =
            energy.indices.map { index ->
                if (counts[index] == 0L) 0f else sqrt(energy[index] / counts[index]).toFloat()
            }
        val peak = rms.maxOrNull() ?: 0f
        return if (peak == 0f) rms else rms.map { (it / peak).coerceIn(0f, 1f) }
    }
}
