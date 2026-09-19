package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.BreathCaptureTracker
import com.pheeeew.core.monitoring.tracker.ClosedBreathSegment
import com.pheeeew.core.monitoring.tracker.SighAttemptTracker

/** Coordinates microphone, breath-segment, and release-gesture events. */
internal class BreathCaptureCoordinator(
    private val captureTracker: BreathCaptureTracker,
    private val attemptTracker: SighAttemptTracker,
    private val attempt: () -> MonitoringSnapshot?,
    private val currentState: () -> MonitoringState,
    private val usable: () -> Boolean,
    private val screen: () -> String,
    private val phase: () -> String,
    private val setPhase: (String) -> Unit,
    private val elapsed: () -> Long,
    private val id: () -> String,
    private val change: (() -> Unit) -> Unit,
    private val emit: (String, MonitoringSnapshot, Map<String, Any>, String?) -> Unit,
    private val observe: () -> Unit,
    private val updateContext: () -> Unit,
    private val flush: () -> Unit,
) {
    private var tapIndex = 0
    private var swipeIndex = 0
    private var releasedCapture: MonitoringSnapshot? = null
    private var releaseStartedElapsed: Long? = null

    fun resetForAttempt() {
        tapIndex = 0
        swipeIndex = 0
        releasedCapture = null
        releaseStartedElapsed = null
        captureTracker.resetForAttempt()
    }

    fun clear() {
        captureTracker.clear()
        releasedCapture = null
        releaseStartedElapsed = null
    }

    fun beginCapture(): MonitoringSnapshot? {
        val origin = attempt() ?: return null
        if (!usable()) return null
        change {
            val started = captureTracker.begin(origin, screen(), elapsed())
            setPhase("listening")
            emit(
                MonitoringEventNames.MIC_START_REQUESTED,
                started.origin,
                mapOf(
                    "capture_index" to started.captureIndex,
                    "trigger" to if (started.captureIndex == 1) "after_memo" else "user_restart",
                ),
                "mic_start:${started.origin.captureId}",
            )
            observe()
        }
        return captureTracker.activeCapture()
    }

    fun microphoneReady() =
        change {
            val active = captureTracker.activeCapture() ?: return@change
            val key = "mic_ready:${active.captureId}"
            if (key in currentState().logicalKeys) return@change
            val ready = captureTracker.markReady(screen(), elapsed()) ?: return@change
            val origin = ready.origin
            emit(
                MonitoringEventNames.MIC_READY,
                origin,
                buildMap {
                    put("capture_index", origin.captureIndex ?: 0)
                    put("start_to_ready_ms", (ready.readyAtElapsed - ready.startedAtElapsed).coerceAtLeast(0))
                    attemptTracker.memoResolvedAt?.let {
                        put("memo_to_ready_ms", (ready.readyAtElapsed - it).coerceAtLeast(0))
                    }
                },
                key,
            )
            observe()
            updateContext()
        }

    fun baseSizeChanged() =
        change {
            val origin = captureTracker.activeCapture() ?: return@change
            emit(
                MonitoringEventNames.BASE_SIZE_CHANGED,
                origin.copy(screen = screen(), state = "listening"),
                mapOf("cause" to "input_activation", "measurement" to "target_state"),
                "base_size:${origin.captureId}",
            )
        }

    fun soundFirstDetected(
        strength: Float,
        activeThreshold: Float,
    ) {
        val origin = captureTracker.detectingCapture(screen()) ?: return
        val key = "first_sound:${origin.captureId}"
        if (!usable() || key in currentState().logicalKeys) return
        change {
            val detectedAt = elapsed()
            emit(
                MonitoringEventNames.SOUND_FIRST_DETECTED,
                origin.copy(screen = screen(), state = "detecting"),
                buildMap {
                    put("strength", strength.coerceIn(0f, 1f))
                    put("active_threshold", activeThreshold.coerceIn(0f, 1f))
                    attemptTracker.startedAt?.let {
                        put("tap_to_detection_ms", (detectedAt - it).coerceAtLeast(0))
                    }
                    captureTracker.readyAtElapsed()?.let {
                        put("mic_ready_to_detection_ms", (detectedAt - it).coerceAtLeast(0))
                    }
                },
                key,
            )
            observe()
        }
    }

    fun breathSample(
        active: Boolean,
        sampleElapsedMs: Long,
        growth: Float,
    ) {
        if (!usable()) return
        val update = captureTracker.recordSample(active, sampleElapsedMs, growth) ?: return
        val origin = captureTracker.activeCapture() ?: return
        if (update.growthStarted) {
            val key = "growth_started:${origin.captureId}"
            if (key !in currentState().logicalKeys) {
                change {
                    emit(
                        MonitoringEventNames.SOUND_GROWTH_STARTED,
                        origin.copy(screen = screen(), state = "detecting"),
                        buildMap {
                            put("growth_before", update.growthBefore)
                            put("growth_after", update.growthAfter)
                            update.readyAtElapsed?.let {
                                put("ready_to_growth_ms", (elapsed() - it).coerceAtLeast(0L))
                            }
                        },
                        key,
                    )
                }
            }
        }
        update.closedSegments.forEach { segment ->
            change { emitBreathSegment(origin, segment) }
        }
    }

    fun releaseReady(
        growth: Float,
        minimumReleaseProgress: Float,
    ) = change {
        val origin = captureTracker.activeCapture() ?: return@change
        emit(
            MonitoringEventNames.SIGH_RELEASE_READY,
            origin.copy(screen = screen(), state = "release_ready"),
            mapOf(
                "growth" to growth.coerceIn(0f, 1f),
                "minimum_release_progress" to minimumReleaseProgress.coerceIn(0f, 1f),
            ),
            "release_ready:${origin.captureId}",
        )
    }

    fun controlTapped(
        controlPhase: String,
        growth: Float,
        inputActive: Boolean,
    ) = change {
        val origin = captureTracker.activeCapture() ?: attempt() ?: return@change
        tapIndex += 1
        emit(
            MonitoringEventNames.SIGH_CONTROL_TAPPED,
            origin.copy(screen = screen(), state = controlPhase),
            mapOf(
                "tap_index" to tapIndex,
                "phase" to controlPhase,
                "growth" to growth.coerceIn(0f, 1f),
                "input_active" to inputActive,
            ),
            null,
        )
    }

    fun swipeAttempted(
        upwardDistanceDp: Float,
        upwardVelocityDpPerSecond: Float,
        growth: Float,
        success: Boolean,
        reason: String,
    ) = change {
        val origin = captureTracker.activeCapture() ?: return@change
        val gestureId = id()
        swipeIndex += 1
        emit(
            MonitoringEventNames.SIGH_SWIPE_ATTEMPTED,
            origin.copy(screen = screen(), state = phase()),
            mapOf(
                "gesture_id" to gestureId,
                "swipe_index" to swipeIndex,
                "upward_distance_dp" to upwardDistanceDp.coerceAtLeast(0f),
                "upward_velocity_dp_s" to upwardVelocityDpPerSecond.coerceAtLeast(0f),
                "growth" to growth.coerceIn(0f, 1f),
                "outcome" to if (success) "success" else "failure",
                "reason" to reason,
            ),
            null,
        )
        if (success) {
            releasedCapture = origin
            releaseStartedElapsed = elapsed()
            emit(
                MonitoringEventNames.SIGH_RELEASE_SUCCEEDED,
                origin.copy(screen = screen(), state = "releasing"),
                mapOf(
                    "gesture_id" to gestureId,
                    "growth_before_release" to growth.coerceIn(0f, 1f),
                ),
                "release_succeeded:${origin.captureId}",
            )
        }
    }

    fun gestureCancelled(reason: String) =
        change {
            val origin = captureTracker.activeCapture() ?: return@change
            emit(
                MonitoringEventNames.SIGH_GESTURE_CANCELLED,
                origin.copy(screen = screen(), state = phase()),
                mapOf("gesture_id" to id(), "reason" to reason),
                null,
            )
        }

    fun releaseAnimationFinished() =
        change {
            val origin = releasedCapture ?: return@change
            val startedAt = releaseStartedElapsed ?: return@change
            emit(
                MonitoringEventNames.SIGH_RELEASE_ANIMATION_FINISHED,
                origin.copy(screen = screen(), state = "released"),
                mapOf("release_to_animation_end_ms" to (elapsed() - startedAt).coerceAtLeast(0L)),
                "release_animation:${origin.captureId}",
            )
            releasedCapture = null
            releaseStartedElapsed = null
        }

    fun endCapture(
        finalGrowth: Float,
        stopReason: String,
        interrupted: Boolean,
    ) {
        val shouldFlush = stopReason == "background" && captureTracker.activeCapture() != null
        change { finishCapture(finalGrowth, stopReason, interrupted) }
        if (shouldFlush) flush()
    }

    fun finishForAttempt(
        finalGrowth: Float,
        stopReason: String,
        interrupted: Boolean,
    ) = finishCapture(finalGrowth, stopReason, interrupted)

    fun microphoneFailed(error: String) =
        change {
            val origin = captureTracker.activeCapture() ?: attempt() ?: return@change
            emit(
                MonitoringEventNames.MIC_FAILED,
                origin.copy(screen = screen(), state = phase()),
                mapOf(
                    "stage" to if (captureTracker.readyAtElapsed() == null) "start" else "read",
                    "reason" to error,
                ),
                null,
            )
            observe()
        }

    private fun finishCapture(
        finalGrowth: Float,
        stopReason: String,
        interrupted: Boolean,
    ) {
        val origin = captureTracker.activeCapture() ?: return
        val key = "breath_summary:${origin.captureId}"
        if (key in currentState().logicalKeys) return
        val finished = captureTracker.finish(stopReason) ?: return
        finished.closedSegments.forEach { segment ->
            emitBreathSegment(origin, segment)
        }
        emit(
            MonitoringEventNames.MIC_STOPPED,
            origin.copy(screen = screen(), state = "stopped"),
            mapOf(
                "reason" to stopReason,
                "ready_observed" to finished.readyObserved,
            ),
            "mic_stopped:${origin.captureId}",
        )
        emit(
            MonitoringEventNames.BREATH_SUMMARY,
            origin.copy(screen = screen(), state = "stopped"),
            mapOf(
                "segment_count" to finished.segmentCount,
                "final_growth" to finalGrowth.coerceIn(0f, 1f),
                "detected" to (finished.segmentCount > 0),
                "observation_quality" to
                    if (interrupted || finished.observationPartial) "partial" else "complete",
                "stop_reason" to stopReason,
                "segment_merge_gap_ms" to SEGMENT_MERGE_GAP_MS,
                "partial_observation_gap_ms" to PARTIAL_OBSERVATION_GAP_MS,
            ),
            key,
        )
        captureTracker.clear()
        observe()
        updateContext()
    }

    private fun emitBreathSegment(
        origin: MonitoringSnapshot,
        segment: ClosedBreathSegment,
    ) {
        emit(
            MonitoringEventNames.BREATH_SEGMENT_ENDED,
            origin.copy(screen = screen(), state = "detecting"),
            buildMap {
                put("segment_index", segment.index)
                put("span_ms", segment.spanMs)
                put("active_ms", segment.activeMs)
                segment.gapBeforeMs?.let { put("gap_before_ms", it.coerceAtLeast(0L)) }
                put("end_reason", segment.endReason)
                put("segment_merge_gap_ms", SEGMENT_MERGE_GAP_MS)
            },
            "breath_segment:${origin.captureId}:${segment.index}",
        )
    }
}
