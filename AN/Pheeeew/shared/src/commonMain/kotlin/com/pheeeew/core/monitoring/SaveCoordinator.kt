package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.SaveTracker

/** Coordinates save requests, API timing, and the delayed save result UI. */
internal class SaveCoordinator(
    private val saveTracker: SaveTracker,
    private val usable: () -> Boolean,
    private val attempt: () -> MonitoringSnapshot?,
    private val activeSave: () -> MonitoringSnapshot?,
    private val screen: () -> String,
    private val phase: () -> String,
    private val setPhase: (String) -> Unit,
    private val currentState: () -> MonitoringState,
    private val replaceState: (MonitoringState) -> Unit,
    private val now: () -> Long,
    private val elapsed: () -> Long,
    private val id: () -> String,
    private val formatTime: (Long) -> String,
    private val formatDay: (Long) -> String,
    private val change: (() -> Unit) -> Unit,
    private val emit: (String, MonitoringSnapshot, Map<String, Any>, String?) -> Unit,
    private val endRecord: (MonitoringSnapshot, String, String, String) -> Unit,
    private val clearAttempt: () -> Unit,
    private val observe: () -> Unit,
    private val updateContext: () -> Unit,
) {
    fun beginSave(): MonitoringSnapshot? {
        if (!usable()) return null
        val origin = attempt() ?: return null
        saveTracker.activeSave?.let { return it }
        change {
            setPhase("submitting")
            val saving = saveTracker.begin(origin, screen(), phase())
            replaceState(currentState().copy(savePending = true))
            observe()
            emit(
                MonitoringEventNames.SAVE_STARTED,
                saving,
                mapOf(
                    "save_index" to saveTracker.saveIndex,
                    "trigger" to if (saveTracker.saveIndex == 1) "release" else "user_retry",
                    "min_display_duration_ms" to 2000,
                ),
                "save_start:${saving.saveAttemptId}",
            )
            updateContext()
        }
        return saveTracker.activeSave
    }

    fun saveResult(
        origin: MonitoringSnapshot?,
        success: Boolean,
        durationMs: Long,
        errorCode: String?,
    ) = change {
        if (origin?.saveAttemptId == null || origin.sighAttemptId == null) return@change
        val key = "save_result:${origin.saveAttemptId}"
        if (key in currentState().logicalKeys) return@change
        val fields =
            buildMap<String, Any> {
                put("outcome", if (success) "success" else "failure")
                if (durationMs >= 0) put("save_operation_duration_ms", durationMs)
                put("creation_kind", "unknown")
                if (!success) put("reason", "unknown")
                errorCode?.let { put("error_code", it) }
            }
        emit(MonitoringEventNames.SAVE_RESULT, origin, fields, key)
        saveTracker.markResult(origin.saveAttemptId)
        if (success) {
            saveTracker.promotePendingToSavedStar()
            val state = currentState()
            if (state.knownNew && state.firstSaved == null) {
                val firstSaved = now()
                replaceState(state.copy(firstSaved = firstSaved))
                emit(
                    MonitoringEventNames.FIRST_SIGH_SAVED,
                    origin,
                    mapOf(
                        "first_saved_at" to formatTime(firstSaved),
                        "cohort_date" to formatDay(firstSaved),
                        "first_save_history" to "known",
                    ),
                    "first_save",
                )
            }
            // If this attempt already ended as unknown, save_result is authoritative. Do not
            // emit a second terminal event; the analytics query joins the later result.
            endRecord(origin.copy(state = "save_result"), "saved", "none", "observed")
            if (attempt()?.sighAttemptId == origin.sighAttemptId) clearAttempt()
        } else if (activeSave()?.saveAttemptId == origin.saveAttemptId) {
            saveTracker.clearActive()
            replaceState(currentState().copy(savePending = false))
            setPhase("save_error")
            updateContext()
        }
    }

    fun saveWaitFinished(
        origin: MonitoringSnapshot?,
        waitMs: Long,
    ) {
        if (!usable() || origin?.saveAttemptId == null) return
        saveTracker.setMinDisplayWait(origin.saveAttemptId, waitMs)
    }

    fun saveUiResultShown(outcome: String) =
        change {
            val observation = saveTracker.pendingSaveUi ?: return@change
            val resultAt = observation.resultAt ?: return@change
            val shownAt = elapsed()
            emit(
                MonitoringEventNames.SAVE_UI_RESULT_SHOWN,
                observation.origin.copy(screen = screen(), state = "save_ui_result"),
                mapOf(
                    "outcome" to outcome,
                    "save_feedback_elapsed_ms" to (shownAt - observation.startedAt).coerceAtLeast(0L),
                    "min_display_wait_ms" to observation.minDisplayWaitMs,
                    "post_result_to_ui_ms" to (shownAt - resultAt).coerceAtLeast(0L),
                    "foreground_continuous" to observation.foregroundContinuous,
                ),
                "save_ui:${observation.origin.saveAttemptId}",
            )
            saveTracker.clearPendingUi()
        }

    fun apiRequestFinished(
        routeTemplate: String,
        method: String,
        durationMs: Long,
        success: Boolean,
        statusCode: Int?,
        errorCode: String?,
        correlationId: String?,
    ) = change {
        val origin = activeSave() ?: saveTracker.pendingSaveUi?.origin ?: return@change
        emit(
            MonitoringEventNames.API_REQUEST_FINISHED,
            origin.copy(screen = screen(), state = phase()),
            buildMap {
                put("http_attempt_id", id())
                put("route_template", routeTemplate)
                put("method", method)
                put("http_duration_ms", durationMs.coerceAtLeast(0L))
                put("outcome", if (success) "success" else "failure")
                statusCode?.let { put("status_code", it) }
                errorCode?.let { put("error_code", it) }
                correlationId?.takeIf(String::isNotBlank)?.let { put("correlation_id", it) }
            },
            null,
        )
    }
}
