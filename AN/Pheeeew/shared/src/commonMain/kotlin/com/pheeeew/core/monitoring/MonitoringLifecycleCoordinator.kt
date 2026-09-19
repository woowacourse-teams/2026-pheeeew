package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.SaveTracker
import com.pheeeew.core.monitoring.tracker.VisitTracker

/** Coordinates visit lifecycle events while leaving event persistence in Monitoring's recorder. */
internal class MonitoringLifecycleCoordinator(
    private val visitTracker: VisitTracker,
    private val saveTracker: SaveTracker,
    private val runtime: MonitoringRuntime,
) {
    fun foreground() {
        runtime.change {
            val snapshot = runtime.currentState()
            val transition = visitTracker.enterForeground(snapshot.visitId) ?: return@change
            transition.interruptedVisitId?.let { interruptedVisitId ->
                val oldVisit =
                    MonitoringSnapshot(
                        interruptedVisitId,
                        screen = snapshot.lastScreen,
                        state = snapshot.lastStage,
                    )
                snapshot.attempt?.let {
                    runtime.endRecord(
                        it,
                        if (snapshot.savePending) "unknown" else "interrupted",
                        "process_interrupted",
                        "inferred",
                    )
                }
                runtime.emit(
                    MonitoringEventNames.APP_VISIT_ENDED,
                    oldVisit,
                    mapOf(
                        "reason" to "process_interrupted",
                        "end_time_quality" to "inferred",
                        "last_observed_at" to MonitoringTime.iso(snapshot.lastObserved),
                    ),
                    "visit_end:${oldVisit.sessionId}",
                )
            }
            transition.endedVisitId?.let { endedVisitId ->
                runtime.attempt()?.let {
                    runtime.endRecord(
                        it,
                        if (runtime.activeSave() != null) "unknown" else "interrupted",
                        "background",
                        "inferred",
                    )
                }
                runtime.emit(
                    MonitoringEventNames.APP_VISIT_ENDED,
                    MonitoringSnapshot(endedVisitId, screen = runtime.screen(), state = runtime.phase()),
                    mapOf(
                        "reason" to "background",
                        "end_time_quality" to "inferred",
                        "last_observed_at" to MonitoringTime.iso(snapshot.lastObserved),
                    ),
                    "visit_end:$endedVisitId",
                )
                // Retain the original attempt for a still-present memo/save UI. Later events carry
                // its original visit; a new foreground does not fabricate a new sigh start.
            }
            if (transition.startsNewVisit) {
                val currentVisitId = visitTracker.ensureVisit()
                val first = snapshot.knownNew && snapshot.firstOpened == null
                runtime.replaceState(
                    runtime.currentState().copy(
                        visitId = currentVisitId,
                        firstVisitId = if (first) currentVisitId else runtime.currentState().firstVisitId,
                    ),
                )
                val origin = visitSnapshot()
                if (first) {
                    runtime.replaceState(runtime.currentState().copy(firstOpened = runtime.now()))
                    runtime.emit(
                        MonitoringEventNames.APP_FIRST_OPENED,
                        origin,
                        mapOf("first_opened_at" to MonitoringTime.iso(runtime.currentState().firstOpened!!)),
                        "first_open",
                    )
                }
                runtime.emit(
                    MonitoringEventNames.APP_VISIT_STARTED,
                    origin,
                    mapOf(
                        "entry_reason" to if (transition.expired) "foreground_return" else "launch",
                        "first_visit" to first,
                    ),
                    "visit_start:$currentVisitId",
                )
            }
            activeDay()
            runtime.observe()
            runtime.updateContext()
        }
        // Also retry SDK-persisted batches that could not finish before the previous suspension.
        runtime.flush()
    }

    fun background() {
        runtime.change {
            if (!visitTracker.isForeground) return@change
            val fields = runtime.attempt()?.sighAttemptId?.let { mapOf("active_sigh_attempt_id" to it) }.orEmpty()
            runtime.emit(MonitoringEventNames.APP_BACKGROUNDED, visitSnapshot(), fields, null)
            runtime.observe()
            visitTracker.leaveForeground()
            saveTracker.markBackgrounded()
            runtime.updateContext()
        }
        runtime.flush()
    }

    fun tick() {
        if (visitTracker.isForeground) {
            val date = MonitoringTime.seoulDay(runtime.now())
            val snapshot = runtime.currentState()
            // Avoid writing the storage every frame/second. Heartbeat only bounds inferred ends.
            if (date !in snapshot.activeDays || runtime.now() - snapshot.lastObserved >= HEARTBEAT_MS) {
                runtime.change {
                    activeDay()
                    runtime.observe()
                }
                return
            }
        }
        runtime.drain()
    }

    fun visitSnapshot(): MonitoringSnapshot =
        MonitoringSnapshot(visitTracker.currentVisitId!!, screen = runtime.screen(), state = runtime.phase())

    private fun activeDay() {
        val date = MonitoringTime.seoulDay(runtime.now())
        val snapshot = runtime.currentState()
        if (date !in snapshot.activeDays) {
            runtime.replaceState(runtime.currentState().copy(activeDays = (snapshot.activeDays + date).takeLast(MAX_LOGICAL_KEYS)))
            runtime.emit(MonitoringEventNames.APP_ACTIVE_DAY, visitSnapshot(), mapOf("activity_date" to date), "day:$date")
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 30_000L
        const val MAX_LOGICAL_KEYS = 4096
    }
}
