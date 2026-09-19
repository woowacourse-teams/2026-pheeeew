package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.SighAttemptTracker
import com.pheeeew.core.monitoring.tracker.VisitTracker

/** Coordinates the memo and permission stages of one sigh attempt. */
internal class SighAttemptCoordinator(
    private val visitTracker: VisitTracker,
    private val attemptTracker: SighAttemptTracker,
    private val usable: () -> Boolean,
    private val isForeground: () -> Boolean,
    private val screen: () -> String,
    private val phase: () -> String,
    private val setPhase: (String) -> Unit,
    private val attempt: () -> MonitoringSnapshot?,
    private val currentState: () -> MonitoringState,
    private val replaceState: (MonitoringState) -> Unit,
    private val elapsed: () -> Long,
    private val id: () -> String,
    private val change: (() -> Unit) -> Unit,
    private val emit: (String, MonitoringSnapshot, Map<String, Any>, String?) -> Unit,
    private val endRecord: (MonitoringSnapshot, String, String, String) -> Unit,
    private val resetForAttempt: () -> Unit,
    private val clearAttempt: () -> Unit,
    private val observe: () -> Unit,
    private val updateContext: () -> Unit,
) {
    fun beginAttempt(guideMode: Boolean): Boolean {
        if (!usable() || !isForeground() || attempt() != null) return false
        change {
            setPhase("starting")
            resetForAttempt()
            val newAttempt = attemptTracker.begin(visitTracker.currentVisitId!!, screen(), phase())
            replaceState(currentState().copy(attempt = newAttempt, savePending = false))
            emit(
                MonitoringEventNames.SIGH_STARTED,
                newAttempt,
                mapOf("entry_point" to "main_button", "guide_mode" to guideMode),
                "start:${newAttempt.sighAttemptId}",
            )
            updateContext()
        }
        return true
    }

    fun startFailed(reason: String) =
        change {
            attempt()?.let {
                emit(
                    MonitoringEventNames.SIGH_START_FAILED,
                    it,
                    mapOf("reason" to reason),
                    "start_failure:${it.sighAttemptId}",
                )
                endRecord(it, "start_failed", reason, "observed")
            }
            clearAttempt()
        }

    fun memoEditing() =
        change {
            setPhase("editing_memo")
            attemptTracker.markMemoEditing()
            observe()
        }

    fun memoShown() =
        change {
            val origin = attempt() ?: return@change
            attemptTracker.markMemoShown()
            emit(
                MonitoringEventNames.MEMO_SHOWN,
                origin.copy(screen = screen(), state = phase()),
                emptyMap(),
                "memo_shown:${origin.sighAttemptId}",
            )
            observe()
        }

    fun memoValidationFailed() =
        change {
            val origin = attempt() ?: return@change
            emit(
                MonitoringEventNames.MEMO_VALIDATION_FAILED,
                origin.copy(screen = screen(), state = phase()),
                mapOf("reason" to "invalid_memo"),
                null,
            )
            observe()
        }

    fun memoCompleted(memoPresent: Boolean) =
        completeMemo(
            MonitoringEventNames.MEMO_COMPLETED,
            mapOf("memo_present" to memoPresent),
        )

    fun memoSkipped() = completeMemo(MonitoringEventNames.MEMO_SKIPPED)

    fun microphonePermissionResult(granted: Boolean) =
        change {
            val origin = attempt() ?: return@change
            emit(
                MonitoringEventNames.PERMISSION_RESULT,
                origin.copy(screen = screen(), state = phase()),
                mapOf(
                    "permission_check_id" to id(),
                    "permission" to "microphone",
                    "status" to if (granted) "granted" else "denied",
                    "context" to "capture_start",
                ),
                null,
            )
            observe()
        }

    private fun completeMemo(
        event: String,
        fields: Map<String, Any> = emptyMap(),
    ) = change {
        val origin = attempt() ?: return@change
        val resolutionKey = "memo_resolution:${origin.sighAttemptId}"
        if (resolutionKey in currentState().logicalKeys) return@change
        val startedAt = attemptTracker.memoShownAt ?: attemptTracker.memoEditingAt ?: elapsed()
        setPhase("awaiting_breath")
        emit(
            event,
            origin.copy(screen = screen(), state = phase()),
            fields + mapOf("memo_elapsed_ms" to (elapsed() - startedAt).coerceAtLeast(0)),
            resolutionKey,
        )
        attemptTracker.markMemoResolved()
        observe()
    }
}
