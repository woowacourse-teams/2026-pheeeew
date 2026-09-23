package com.pheeeew.legacy.core.monitoring

import com.pheeeew.legacy.core.monitoring.tracker.SaveTracker

/** Coordinates save requests, API timing, and the delayed save result UI. */
internal class SaveCoordinator(
    private val saveTracker: SaveTracker,
    private val runtime: MonitoringRuntime,
) {
    fun beginSave(): MonitoringSnapshot? {
        if (!runtime.usable()) return null
        val origin = runtime.attempt() ?: return null
        saveTracker.activeSave?.let { return it }
        runtime.change {
            runtime.setPhase("submitting")
            val saving = saveTracker.begin(origin, runtime.screen(), runtime.phase())
            runtime.replaceState(runtime.currentState().copy(savePending = true))
            runtime.observe()
            runtime.emit(
                MonitoringEventNames.SAVE_STARTED,
                saving,
                mapOf(
                    "save_index" to saveTracker.saveIndex,
                    "trigger" to if (saveTracker.saveIndex == 1) "release" else "user_retry",
                    "min_display_duration_ms" to 2000,
                ),
                "save_start:${saving.saveAttemptId}",
            )
            runtime.updateContext()
        }
        return saveTracker.activeSave
    }

    fun saveResult(
        origin: MonitoringSnapshot?,
        success: Boolean,
        durationMs: Long,
        errorCode: String?,
    ) = runtime.change {
        if (origin?.saveAttemptId == null || origin.sighAttemptId == null) return@change
        val key = "save_result:${origin.saveAttemptId}"
        if (key in runtime.currentState().logicalKeys) return@change
        val fields =
            buildMap<String, Any> {
                put("outcome", if (success) "success" else "failure")
                if (durationMs >= 0) put("save_operation_duration_ms", durationMs)
                put("creation_kind", "unknown")
                if (!success) put("reason", "unknown")
                errorCode?.let { put("error_code", it) }
            }
        runtime.emit(MonitoringEventNames.SAVE_RESULT, origin, fields, key)
        saveTracker.markResult(origin.saveAttemptId)
        if (success) {
            saveTracker.promotePendingToSavedStar()
            val state = runtime.currentState()
            if (state.knownNew && state.firstSaved == null) {
                val firstSaved = runtime.now()
                runtime.replaceState(state.copy(firstSaved = firstSaved))
                runtime.emit(
                    MonitoringEventNames.FIRST_SIGH_SAVED,
                    origin,
                    mapOf(
                        "first_saved_at" to MonitoringTime.iso(firstSaved),
                        "cohort_date" to MonitoringTime.seoulDay(firstSaved),
                        "first_save_history" to "known",
                    ),
                    "first_save",
                )
            }
            // If this attempt already ended as unknown, save_result is authoritative. Do not
            // emit a second terminal event; the analytics query joins the later result.
            runtime.endRecord(origin.copy(state = "save_result"), "saved", "none", "observed")
            if (runtime.attempt()?.sighAttemptId == origin.sighAttemptId) runtime.clearAttempt()
        } else if (runtime.activeSave()?.saveAttemptId == origin.saveAttemptId) {
            saveTracker.clearActive()
            runtime.replaceState(runtime.currentState().copy(savePending = false))
            runtime.setPhase("save_error")
            runtime.updateContext()
        }
    }

    fun saveWaitFinished(
        origin: MonitoringSnapshot?,
        waitMs: Long,
    ) {
        if (!runtime.usable() || origin?.saveAttemptId == null) return
        saveTracker.setMinDisplayWait(origin.saveAttemptId, waitMs)
    }

    fun saveUiResultShown(outcome: String) =
        runtime.change {
            val observation = saveTracker.pendingSaveUi ?: return@change
            val resultAt = observation.resultAt ?: return@change
            val shownAt = runtime.elapsed()
            runtime.emit(
                MonitoringEventNames.SAVE_UI_RESULT_SHOWN,
                observation.origin.copy(screen = runtime.screen(), state = "save_ui_result"),
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
    ) = runtime.change {
        val origin = runtime.activeSave() ?: saveTracker.pendingSaveUi?.origin ?: return@change
        runtime.emit(
            MonitoringEventNames.API_REQUEST_FINISHED,
            origin.copy(screen = runtime.screen(), state = runtime.phase()),
            buildMap {
                put("http_attempt_id", runtime.id())
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
