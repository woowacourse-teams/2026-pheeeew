package com.pheeeew.legacy.core.monitoring

import com.pheeeew.legacy.core.monitoring.tracker.SighAttemptTracker
import com.pheeeew.legacy.core.monitoring.tracker.VisitTracker

/** Coordinates the memo and permission stages of one sigh attempt. */
internal class SighAttemptCoordinator(
    private val visitTracker: VisitTracker,
    private val attemptTracker: SighAttemptTracker,
    private val isForeground: () -> Boolean,
    private val runtime: MonitoringRuntime,
) {
    fun beginAttempt(guideMode: Boolean): Boolean {
        if (!runtime.usable() || !isForeground() || runtime.attempt() != null) return false
        runtime.change {
            runtime.setPhase("starting")
            runtime.resetForAttempt()
            val newAttempt = attemptTracker.begin(visitTracker.currentVisitId!!, runtime.screen(), runtime.phase())
            runtime.replaceState(runtime.currentState().copy(attempt = newAttempt, savePending = false))
            runtime.emit(
                MonitoringEventNames.SIGH_STARTED,
                newAttempt,
                mapOf("entry_point" to "main_button", "guide_mode" to guideMode),
                "start:${newAttempt.sighAttemptId}",
            )
            runtime.updateContext()
        }
        return true
    }

    fun startFailed(reason: String) =
        runtime.change {
            runtime.attempt()?.let {
                runtime.emit(
                    MonitoringEventNames.SIGH_START_FAILED,
                    it,
                    mapOf("reason" to reason),
                    "start_failure:${it.sighAttemptId}",
                )
                runtime.endRecord(it, "start_failed", reason, "observed")
            }
            runtime.clearAttempt()
        }

    fun memoEditing() =
        runtime.change {
            runtime.setPhase("editing_memo")
            attemptTracker.markMemoEditing()
            runtime.observe()
        }

    fun memoShown() =
        runtime.change {
            val origin = runtime.attempt() ?: return@change
            attemptTracker.markMemoShown()
            runtime.emit(
                MonitoringEventNames.MEMO_SHOWN,
                origin.copy(screen = runtime.screen(), state = runtime.phase()),
                emptyMap(),
                "memo_shown:${origin.sighAttemptId}",
            )
            runtime.observe()
        }

    fun memoValidationFailed() =
        runtime.change {
            val origin = runtime.attempt() ?: return@change
            runtime.emit(
                MonitoringEventNames.MEMO_VALIDATION_FAILED,
                origin.copy(screen = runtime.screen(), state = runtime.phase()),
                mapOf("reason" to "invalid_memo"),
                null,
            )
            runtime.observe()
        }

    fun memoCompleted(memoPresent: Boolean) =
        completeMemo(
            MonitoringEventNames.MEMO_COMPLETED,
            mapOf("memo_present" to memoPresent),
        )

    fun memoSkipped() = completeMemo(MonitoringEventNames.MEMO_SKIPPED)

    fun microphonePermissionResult(granted: Boolean) =
        runtime.change {
            val origin = runtime.attempt() ?: return@change
            runtime.emit(
                MonitoringEventNames.PERMISSION_RESULT,
                origin.copy(screen = runtime.screen(), state = runtime.phase()),
                mapOf(
                    "permission_check_id" to runtime.id(),
                    "permission" to "microphone",
                    "status" to if (granted) "granted" else "denied",
                    "context" to "capture_start",
                ),
                null,
            )
            runtime.observe()
        }

    private fun completeMemo(
        event: MonitoringEventDefinition,
        fields: Map<String, Any> = emptyMap(),
    ) = runtime.change {
        val origin = runtime.attempt() ?: return@change
        val resolutionKey = "memo_resolution:${origin.sighAttemptId}"
        if (resolutionKey in runtime.currentState().logicalKeys) return@change
        val startedAt = attemptTracker.memoShownAt ?: attemptTracker.memoEditingAt ?: runtime.elapsed()
        runtime.setPhase("awaiting_breath")
        runtime.emit(
            event,
            origin.copy(screen = runtime.screen(), state = runtime.phase()),
            fields + mapOf("memo_elapsed_ms" to (runtime.elapsed() - startedAt).coerceAtLeast(0)),
            resolutionKey,
        )
        attemptTracker.markMemoResolved()
        runtime.observe()
    }
}
