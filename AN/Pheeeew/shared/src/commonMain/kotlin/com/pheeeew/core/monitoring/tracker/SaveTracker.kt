package com.pheeeew.core.monitoring.tracker

import com.pheeeew.core.monitoring.MonitoringSnapshot
import com.pheeeew.core.monitoring.SaveObservation

/** Owns save-attempt identity and the two UI observations around the save result. */
internal class SaveTracker(
    private val id: () -> String,
    private val elapsed: () -> Long,
) {
    var activeSave: MonitoringSnapshot? = null
        private set

    var saveIndex: Int = 0
        private set

    var pendingSaveUi: SaveObservation? = null
        private set

    var pendingSavedStar: SaveObservation? = null
        private set

    fun resetForAttempt() {
        saveIndex = 0
    }

    fun begin(
        origin: MonitoringSnapshot,
        screen: String,
        state: String,
    ): MonitoringSnapshot {
        saveIndex += 1
        activeSave = origin.copy(saveAttemptId = id(), screen = screen, state = state)
        pendingSaveUi = SaveObservation(activeSave!!, elapsed())
        return activeSave!!
    }

    fun markResult(saveAttemptId: String) {
        pendingSaveUi
            ?.takeIf { it.origin.saveAttemptId == saveAttemptId }
            ?.resultAt = elapsed()
    }

    fun promotePendingToSavedStar() {
        pendingSavedStar = pendingSaveUi
    }

    fun setMinDisplayWait(
        saveAttemptId: String,
        waitMs: Long,
    ) {
        pendingSaveUi
            ?.takeIf { it.origin.saveAttemptId == saveAttemptId }
            ?.minDisplayWaitMs = waitMs.coerceAtLeast(0L)
    }

    fun markBackgrounded() {
        pendingSaveUi?.foregroundContinuous = false
        pendingSavedStar?.foregroundContinuous = false
    }

    fun clearActive() {
        activeSave = null
    }

    fun clearPendingUi() {
        pendingSaveUi = null
    }

    fun clearPendingSavedStar() {
        pendingSavedStar = null
    }
}
