package com.pheeeew.core.monitoring.tracker

internal data class ForegroundTransition(
    val expired: Boolean,
    val interruptedVisitId: String?,
    val endedVisitId: String?,
    val startsNewVisit: Boolean,
)

/** Owns the in-process visit lifecycle; event emission remains coordinated by Monitoring. */
internal class VisitTracker(
    private val id: () -> String,
    private val elapsed: () -> Long,
    private val timeoutMs: Long,
) {
    private var backgroundStartedAt: Long? = null

    var isForeground: Boolean = false
        private set

    var currentVisitId: String? = null
        private set

    fun enterForeground(persistedVisitId: String?): ForegroundTransition? {
        if (isForeground) return null
        val expired = backgroundStartedAt?.let { elapsed() - it >= timeoutMs } == true
        val interruptedVisitId = if (currentVisitId == null) persistedVisitId else null
        val endedVisitId = if (expired) currentVisitId else null
        if (expired) currentVisitId = null
        isForeground = true
        backgroundStartedAt = null
        return ForegroundTransition(
            expired = expired,
            interruptedVisitId = interruptedVisitId,
            endedVisitId = endedVisitId,
            startsNewVisit = currentVisitId == null,
        )
    }

    fun ensureVisit(): String {
        if (currentVisitId == null) currentVisitId = id()
        return currentVisitId!!
    }

    fun leaveForeground(): String? {
        if (!isForeground) return null
        isForeground = false
        backgroundStartedAt = elapsed()
        return currentVisitId
    }
}
