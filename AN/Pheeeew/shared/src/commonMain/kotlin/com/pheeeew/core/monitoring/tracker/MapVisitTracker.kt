package com.pheeeew.core.monitoring.tracker

import com.pheeeew.core.monitoring.MonitoringSnapshot

/** Owns the active map visit and the currently selected star. */
internal class MapVisitTracker(
    private val id: () -> String,
) {
    var activeMapVisit: MonitoringSnapshot? = null
        private set

    var activeSelection: MonitoringSnapshot? = null
        private set

    var activeSelectionSource: String? = null
        private set

    fun startVisit(sessionId: String): MonitoringSnapshot? {
        if (activeMapVisit != null) return null
        val origin =
            MonitoringSnapshot(
                sessionId = sessionId,
                mapVisitId = id(),
                screen = "map",
                state = "visible",
            )
        activeMapVisit = origin
        return origin
    }

    fun selectStar(
        mapVisit: MonitoringSnapshot,
        entrySource: String,
    ): MonitoringSnapshot {
        val origin = mapVisit.copy(selectionId = id(), state = "star_selected")
        activeSelection = origin
        activeSelectionSource = entrySource
        return origin
    }

    fun clearSelection() {
        activeSelection = null
        activeSelectionSource = null
    }

    fun clearVisit() {
        activeMapVisit = null
    }
}
