package com.pheeeew.core.monitoring.tracker

import com.pheeeew.core.monitoring.MonitoringSnapshot
import com.pheeeew.core.monitoring.PARTIAL_OBSERVATION_GAP_MS
import com.pheeeew.core.monitoring.SEGMENT_MERGE_GAP_MS

internal data class BreathCaptureStarted(
    val origin: MonitoringSnapshot,
    val captureIndex: Int,
    val startedAtElapsed: Long,
)

internal data class BreathCaptureReady(
    val origin: MonitoringSnapshot,
    val startedAtElapsed: Long,
    val readyAtElapsed: Long,
)

internal data class ClosedBreathSegment(
    val index: Int,
    val spanMs: Long,
    val activeMs: Long,
    val gapBeforeMs: Long?,
    val endReason: String,
)

internal data class BreathSampleUpdate(
    val growthBefore: Float,
    val growthAfter: Float,
    val growthStarted: Boolean,
    val readyAtElapsed: Long?,
    val closedSegments: List<ClosedBreathSegment>,
)

internal data class BreathCaptureFinished(
    val origin: MonitoringSnapshot,
    val segmentCount: Int,
    val readyObserved: Boolean,
    val observationPartial: Boolean,
    val closedSegments: List<ClosedBreathSegment>,
)

/** Owns microphone capture identity and all breath-measurement state for one attempt. */
internal class BreathCaptureTracker(
    private val id: () -> String,
) {
    private var activeCapture: MonitoringSnapshot? = null
    private var startedAtElapsed: Long? = null
    private var readyAtElapsed: Long? = null
    private var observedMs = 0L
    private var segment: ActiveBreathSegment? = null
    private var segmentCount = 0
    private var lastClosedAtMs: Long? = null
    private var silentGapMs = 0L
    private var lastSampleActive = false
    private var observationPartial = false
    private var lastGrowth = 0f
    private var captureIndex = 0

    fun resetForAttempt() {
        captureIndex = 0
        clear()
    }

    fun begin(
        origin: MonitoringSnapshot,
        screen: String,
        startedAtElapsed: Long,
    ): BreathCaptureStarted {
        captureIndex += 1
        clearMeasurementState()
        val capture =
            origin.copy(
                captureId = id(),
                captureIndex = captureIndex,
                screen = screen,
                state = "listening",
            )
        activeCapture = capture
        this.startedAtElapsed = startedAtElapsed
        return BreathCaptureStarted(capture, captureIndex, startedAtElapsed)
    }

    fun activeCapture(): MonitoringSnapshot? = activeCapture

    fun startedAtElapsed(): Long? = startedAtElapsed

    fun readyAtElapsed(): Long? = readyAtElapsed

    fun lastGrowth(): Float = lastGrowth

    fun markReady(
        screen: String,
        readyAtElapsed: Long,
    ): BreathCaptureReady? {
        val origin = activeCapture ?: return null
        val startedAt = startedAtElapsed ?: return null
        this.readyAtElapsed = readyAtElapsed
        val ready = origin.copy(screen = screen, state = "ready")
        activeCapture = ready
        return BreathCaptureReady(ready, startedAt, readyAtElapsed)
    }

    fun detectingCapture(screen: String): MonitoringSnapshot? =
        activeCapture?.copy(screen = screen, state = "detecting")

    fun recordSample(
        active: Boolean,
        sampleElapsedMs: Long,
        growth: Float,
    ): BreathSampleUpdate? {
        if (activeCapture == null || readyAtElapsed == null) return null

        val previousGrowth = lastGrowth
        lastGrowth = growth.coerceIn(0f, 1f)
        val closedSegments = mutableListOf<ClosedBreathSegment>()
        val gapMs = sampleElapsedMs.coerceAtLeast(0L)
        observedMs += gapMs

        if (gapMs > PARTIAL_OBSERVATION_GAP_MS) {
            observationPartial = true
            closeSegment("observation_gap")?.let(closedSegments::add)
            silentGapMs = 0L
            lastSampleActive = false
        }

        if (active) {
            val currentSegment = segment
            if (currentSegment == null) {
                segmentCount += 1
                segment =
                    ActiveBreathSegment(
                        index = segmentCount,
                        startedAtMs = observedMs,
                        lastActiveAtMs = observedMs,
                        gapBeforeMs = lastClosedAtMs?.let { observedMs - it },
                    )
            } else {
                if (lastSampleActive && gapMs <= PARTIAL_OBSERVATION_GAP_MS) {
                    currentSegment.activeMs += gapMs
                }
                currentSegment.lastActiveAtMs = observedMs
            }
            silentGapMs = 0L
        } else if (segment != null) {
            silentGapMs += gapMs
            if (silentGapMs > SEGMENT_MERGE_GAP_MS) {
                closeSegment("silence_gap")?.let(closedSegments::add)
            }
        }
        lastSampleActive = active

        return BreathSampleUpdate(
            growthBefore = previousGrowth,
            growthAfter = lastGrowth,
            growthStarted = active && lastGrowth > previousGrowth,
            readyAtElapsed = readyAtElapsed,
            closedSegments = closedSegments,
        )
    }

    fun finish(stopReason: String): BreathCaptureFinished? {
        val origin = activeCapture ?: return null
        val closedSegments = closeSegment(stopReason)?.let(::listOf).orEmpty()
        return BreathCaptureFinished(
            origin = origin,
            segmentCount = segmentCount,
            readyObserved = readyAtElapsed != null,
            observationPartial = observationPartial,
            closedSegments = closedSegments,
        )
    }

    fun clear() {
        activeCapture = null
        startedAtElapsed = null
        clearMeasurementState()
    }

    private fun clearMeasurementState() {
        readyAtElapsed = null
        observedMs = 0L
        segment = null
        segmentCount = 0
        lastClosedAtMs = null
        silentGapMs = 0L
        lastSampleActive = false
        observationPartial = false
        lastGrowth = 0f
    }

    private fun closeSegment(endReason: String): ClosedBreathSegment? {
        val currentSegment = segment ?: return null
        val closed =
            ClosedBreathSegment(
                index = currentSegment.index,
                spanMs = (currentSegment.lastActiveAtMs - currentSegment.startedAtMs).coerceAtLeast(0L),
                activeMs = currentSegment.activeMs.coerceAtLeast(0L),
                gapBeforeMs = currentSegment.gapBeforeMs,
                endReason = endReason,
            )
        lastClosedAtMs = currentSegment.lastActiveAtMs
        segment = null
        silentGapMs = 0L
        return closed
    }

    private data class ActiveBreathSegment(
        val index: Int,
        val startedAtMs: Long,
        var lastActiveAtMs: Long,
        var activeMs: Long = 0L,
        val gapBeforeMs: Long? = null,
    )
}
