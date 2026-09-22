package com.pheeeew.core.monitoring.tracker

import com.pheeeew.core.monitoring.MonitoringSnapshot

/** Owns the identity and timing markers for one sigh attempt. */
internal class SighAttemptTracker(
    private val id: () -> String,
    private val elapsed: () -> Long,
) {
    var snapshot: MonitoringSnapshot? = null
        private set

    var startedAt: Long? = null
        private set

    var memoEditingAt: Long? = null
        private set

    var memoShownAt: Long? = null
        private set

    var memoResolvedAt: Long? = null
        private set

    fun begin(
        sessionId: String,
        screen: String,
        state: String,
    ): MonitoringSnapshot {
        startedAt = elapsed()
        memoEditingAt = null
        memoShownAt = null
        memoResolvedAt = null
        snapshot = MonitoringSnapshot(sessionId, id(), screen = screen, state = state)
        return snapshot!!
    }

    fun markMemoEditing() {
        memoEditingAt = elapsed()
    }

    fun markMemoShown() {
        if (memoShownAt == null) memoShownAt = elapsed()
    }

    fun markMemoResolved() {
        memoResolvedAt = elapsed()
    }

    fun clear() {
        snapshot = null
        startedAt = null
        memoEditingAt = null
        memoShownAt = null
        memoResolvedAt = null
    }
}
