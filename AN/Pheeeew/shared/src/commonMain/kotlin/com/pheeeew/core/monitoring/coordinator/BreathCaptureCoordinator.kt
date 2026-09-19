package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.BreathCaptureTracker
import com.pheeeew.core.monitoring.tracker.ClosedBreathSegment
import com.pheeeew.core.monitoring.tracker.SighAttemptTracker

/** Coordinates microphone, breath-segment, and release-gesture events. */
internal class BreathCaptureCoordinator(
    private val captureTracker: BreathCaptureTracker,
    private val attemptTracker: SighAttemptTracker,
    private val runtime: MonitoringRuntime,
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
        val origin = runtime.attempt() ?: return null
        if (!runtime.usable()) return null
        runtime.change {
            val started = captureTracker.begin(origin, runtime.screen(), runtime.elapsed())
            runtime.setPhase("listening")
            runtime.emit(
                MonitoringEventNames.MIC_START_REQUESTED,
                started.origin,
                mapOf(
                    "capture_index" to started.captureIndex,
                    "trigger" to if (started.captureIndex == 1) "after_memo" else "user_restart",
                ),
                "mic_start:${started.origin.captureId}",
            )
            runtime.observe()
        }
        return captureTracker.activeCapture()
    }

    fun microphoneReady() =
        runtime.change {
            val active = captureTracker.activeCapture() ?: return@change
            val key = "mic_ready:${active.captureId}"
            if (key in runtime.currentState().logicalKeys) return@change
            val ready = captureTracker.markReady(runtime.screen(), runtime.elapsed()) ?: return@change
            val origin = ready.origin
            runtime.emit(
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
            runtime.observe()
            runtime.updateContext()
        }

    fun baseSizeChanged() =
        runtime.change {
            val origin = captureTracker.activeCapture() ?: return@change
            runtime.emit(
                MonitoringEventNames.BASE_SIZE_CHANGED,
                origin.copy(screen = runtime.screen(), state = "listening"),
                mapOf("cause" to "input_activation", "measurement" to "target_state"),
                "base_size:${origin.captureId}",
            )
        }

    fun soundFirstDetected(
        strength: Float,
        activeThreshold: Float,
    ) {
        val origin = captureTracker.detectingCapture(runtime.screen()) ?: return
        val key = "first_sound:${origin.captureId}"
        if (!runtime.usable() || key in runtime.currentState().logicalKeys) return
        runtime.change {
            val detectedAt = runtime.elapsed()
            runtime.emit(
                MonitoringEventNames.SOUND_FIRST_DETECTED,
                origin.copy(screen = runtime.screen(), state = "detecting"),
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
            runtime.observe()
        }
    }

    fun breathSample(
        active: Boolean,
        sampleElapsedMs: Long,
        growth: Float,
    ) {
        if (!runtime.usable()) return
        val update = captureTracker.recordSample(active, sampleElapsedMs, growth) ?: return
        val origin = captureTracker.activeCapture() ?: return
        if (update.growthStarted) {
            val key = "growth_started:${origin.captureId}"
            if (key !in runtime.currentState().logicalKeys) {
                runtime.change {
                    runtime.emit(
                        MonitoringEventNames.SOUND_GROWTH_STARTED,
                        origin.copy(screen = runtime.screen(), state = "detecting"),
                        buildMap {
                            put("growth_before", update.growthBefore)
                            put("growth_after", update.growthAfter)
                            update.readyAtElapsed?.let {
                                put("ready_to_growth_ms", (runtime.elapsed() - it).coerceAtLeast(0L))
                            }
                        },
                        key,
                    )
                }
            }
        }
        update.closedSegments.forEach { segment ->
        runtime.change { emitBreathSegment(origin, segment) }
        }
    }

    fun releaseReady(
        growth: Float,
        minimumReleaseProgress: Float,
    ) = runtime.change {
        val origin = captureTracker.activeCapture() ?: return@change
        runtime.emit(
            MonitoringEventNames.SIGH_RELEASE_READY,
            origin.copy(screen = runtime.screen(), state = "release_ready"),
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
    ) = runtime.change {
        val origin = captureTracker.activeCapture() ?: runtime.attempt() ?: return@change
        tapIndex += 1
        runtime.emit(
            MonitoringEventNames.SIGH_CONTROL_TAPPED,
            origin.copy(screen = runtime.screen(), state = controlPhase),
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
    ) = runtime.change {
        val origin = captureTracker.activeCapture() ?: return@change
        val gestureId = runtime.id()
        swipeIndex += 1
        runtime.emit(
            MonitoringEventNames.SIGH_SWIPE_ATTEMPTED,
            origin.copy(screen = runtime.screen(), state = runtime.phase()),
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
            releaseStartedElapsed = runtime.elapsed()
            runtime.emit(
                MonitoringEventNames.SIGH_RELEASE_SUCCEEDED,
                origin.copy(screen = runtime.screen(), state = "releasing"),
                mapOf(
                    "gesture_id" to gestureId,
                    "growth_before_release" to growth.coerceIn(0f, 1f),
                ),
                "release_succeeded:${origin.captureId}",
            )
        }
    }

    fun gestureCancelled(reason: String) =
        runtime.change {
            val origin = captureTracker.activeCapture() ?: return@change
            runtime.emit(
                MonitoringEventNames.SIGH_GESTURE_CANCELLED,
                origin.copy(screen = runtime.screen(), state = runtime.phase()),
                mapOf("gesture_id" to runtime.id(), "reason" to reason),
                null,
            )
        }

    fun releaseAnimationFinished() =
        runtime.change {
            val origin = releasedCapture ?: return@change
            val startedAt = releaseStartedElapsed ?: return@change
            runtime.emit(
                MonitoringEventNames.SIGH_RELEASE_ANIMATION_FINISHED,
                origin.copy(screen = runtime.screen(), state = "released"),
                mapOf("release_to_animation_end_ms" to (runtime.elapsed() - startedAt).coerceAtLeast(0L)),
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
        runtime.change { finishCapture(finalGrowth, stopReason, interrupted) }
        if (shouldFlush) runtime.flush()
    }

    fun finishForAttempt(
        finalGrowth: Float,
        stopReason: String,
        interrupted: Boolean,
    ) = finishCapture(finalGrowth, stopReason, interrupted)

    fun microphoneFailed(error: String) =
        runtime.change {
            val origin = captureTracker.activeCapture() ?: runtime.attempt() ?: return@change
            runtime.emit(
                MonitoringEventNames.MIC_FAILED,
                origin.copy(screen = runtime.screen(), state = runtime.phase()),
                mapOf(
                    "stage" to if (captureTracker.readyAtElapsed() == null) "start" else "read",
                    "reason" to error,
                ),
                null,
            )
            runtime.observe()
        }

    private fun finishCapture(
        finalGrowth: Float,
        stopReason: String,
        interrupted: Boolean,
    ) {
        val origin = captureTracker.activeCapture() ?: return
        val key = "breath_summary:${origin.captureId}"
        if (key in runtime.currentState().logicalKeys) return
        val finished = captureTracker.finish(stopReason) ?: return
        finished.closedSegments.forEach { segment ->
            emitBreathSegment(origin, segment)
        }
        runtime.emit(
            MonitoringEventNames.MIC_STOPPED,
            origin.copy(screen = runtime.screen(), state = "stopped"),
            mapOf(
                "reason" to stopReason,
                "ready_observed" to finished.readyObserved,
            ),
            "mic_stopped:${origin.captureId}",
        )
        runtime.emit(
            MonitoringEventNames.BREATH_SUMMARY,
            origin.copy(screen = runtime.screen(), state = "stopped"),
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
        runtime.observe()
        runtime.updateContext()
    }

    private fun emitBreathSegment(
        origin: MonitoringSnapshot,
        segment: ClosedBreathSegment,
    ) {
        runtime.emit(
            MonitoringEventNames.BREATH_SEGMENT_ENDED,
            origin.copy(screen = runtime.screen(), state = "detecting"),
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
