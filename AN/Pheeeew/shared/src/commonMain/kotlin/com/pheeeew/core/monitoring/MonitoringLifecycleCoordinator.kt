package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.SaveTracker
import com.pheeeew.core.monitoring.tracker.VisitTracker
import kotlin.time.Instant

/** Coordinates visit lifecycle events while leaving event persistence in Monitoring's recorder. */
internal class MonitoringLifecycleCoordinator(
    private val visitTracker: VisitTracker,
    private val saveTracker: SaveTracker,
    private val now: () -> Long,
    private val currentState: () -> MonitoringState,
    private val replaceState: (MonitoringState) -> Unit,
    private val screen: () -> String,
    private val phase: () -> String,
    private val attempt: () -> MonitoringSnapshot?,
    private val activeSave: () -> MonitoringSnapshot?,
    private val change: (() -> Unit) -> Unit,
    private val emit: (String, MonitoringSnapshot, Map<String, Any>, String?) -> Unit,
    private val endRecord: (MonitoringSnapshot, String, String, String) -> Unit,
    private val observe: () -> Unit,
    private val updateContext: () -> Unit,
) {
    fun foreground(onFlush: () -> Unit) {
        change {
            val snapshot = currentState()
            val transition = visitTracker.enterForeground(snapshot.visitId) ?: return@change
            transition.interruptedVisitId?.let { interruptedVisitId ->
                val oldVisit =
                    MonitoringSnapshot(
                        interruptedVisitId,
                        screen = snapshot.lastScreen,
                        state = snapshot.lastStage,
                    )
                snapshot.attempt?.let {
                    endRecord(
                        it,
                        if (snapshot.savePending) "unknown" else "interrupted",
                        "process_interrupted",
                        "inferred",
                    )
                }
                emit(
                    MonitoringEventNames.APP_VISIT_ENDED,
                    oldVisit,
                    mapOf(
                        "reason" to "process_interrupted",
                        "end_time_quality" to "inferred",
                        "last_observed_at" to isoTime(snapshot.lastObserved),
                    ),
                    "visit_end:${oldVisit.sessionId}",
                )
            }
            transition.endedVisitId?.let { endedVisitId ->
                attempt()?.let {
                    endRecord(
                        it,
                        if (activeSave() != null) "unknown" else "interrupted",
                        "background",
                        "inferred",
                    )
                }
                emit(
                    MonitoringEventNames.APP_VISIT_ENDED,
                    MonitoringSnapshot(endedVisitId, screen = screen(), state = phase()),
                    mapOf(
                        "reason" to "background",
                        "end_time_quality" to "inferred",
                        "last_observed_at" to isoTime(snapshot.lastObserved),
                    ),
                    "visit_end:$endedVisitId",
                )
                // Retain the original attempt for a still-present memo/save UI. Later events carry
                // its original visit; a new foreground does not fabricate a new sigh start.
            }
            if (transition.startsNewVisit) {
                val currentVisitId = visitTracker.ensureVisit()
                val first = snapshot.knownNew && snapshot.firstOpened == null
                replaceState(
                    currentState().copy(
                        visitId = currentVisitId,
                        firstVisitId = if (first) currentVisitId else currentState().firstVisitId,
                    ),
                )
                val origin = visitSnapshot()
                if (first) {
                    replaceState(currentState().copy(firstOpened = now()))
                    emit(
                        MonitoringEventNames.APP_FIRST_OPENED,
                        origin,
                        mapOf("first_opened_at" to isoTime(currentState().firstOpened!!)),
                        "first_open",
                    )
                }
                emit(
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
            observe()
            updateContext()
        }
        // Also retry SDK-persisted batches that could not finish before the previous suspension.
        onFlush()
    }

    fun background(onFlush: () -> Unit) {
        change {
            if (!visitTracker.isForeground) return@change
            val fields = attempt()?.sighAttemptId?.let { mapOf("active_sigh_attempt_id" to it) }.orEmpty()
            emit(MonitoringEventNames.APP_BACKGROUNDED, visitSnapshot(), fields, null)
            observe()
            visitTracker.leaveForeground()
            saveTracker.markBackgrounded()
            updateContext()
        }
        onFlush()
    }

    fun tick(onDrain: () -> Unit) {
        if (visitTracker.isForeground) {
            val date = seoulDay(now())
            val snapshot = currentState()
            // Avoid writing the storage every frame/second. Heartbeat only bounds inferred ends.
            if (date !in snapshot.activeDays || now() - snapshot.lastObserved >= HEARTBEAT_MS) {
                change {
                    activeDay()
                    observe()
                }
                return
            }
        }
        onDrain()
    }

    fun visitSnapshot(): MonitoringSnapshot =
        MonitoringSnapshot(visitTracker.currentVisitId!!, screen = screen(), state = phase())

    private fun activeDay() {
        val date = seoulDay(now())
        val snapshot = currentState()
        if (date !in snapshot.activeDays) {
            replaceState(currentState().copy(activeDays = (snapshot.activeDays + date).takeLast(MAX_LOGICAL_KEYS)))
            emit(MonitoringEventNames.APP_ACTIVE_DAY, visitSnapshot(), mapOf("activity_date" to date), "day:$date")
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 30_000L
        const val MAX_LOGICAL_KEYS = 4096
    }
}

private fun isoTime(time: Long) =
    Instant
        .fromEpochMilliseconds(time)
        .toString()

private fun seoulDay(time: Long) = isoTime(time + 9 * 60 * 60 * 1000).take(10)
