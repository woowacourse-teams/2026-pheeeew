package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.MapVisitTracker
import com.pheeeew.core.monitoring.tracker.SaveTracker
import com.pheeeew.core.monitoring.tracker.VisitTracker

/** Coordinates map visits, star selection, and the saved-star handoff. */
internal class MapVisitCoordinator(
    private val visitTracker: VisitTracker,
    private val mapVisitTracker: MapVisitTracker,
    private val saveTracker: SaveTracker,
    private val elapsed: () -> Long,
    private val change: (() -> Unit) -> Unit,
    private val emit: (String, MonitoringSnapshot, Map<String, Any>, String?) -> Unit,
    private val updateContext: () -> Unit,
) {
    fun mapVisitStarted(entryReason: String) =
        change {
            val sessionId = visitTracker.currentVisitId ?: return@change
            val origin = mapVisitTracker.startVisit(sessionId) ?: return@change
            emit(
                MonitoringEventNames.MAP_VISIT_STARTED,
                origin,
                mapOf("entry_reason" to entryReason),
                "map_visit_start:${origin.mapVisitId}",
            )
            updateContext()
        }

    fun mapStarsVisible(visibleStarCount: Int) =
        change {
            if (visibleStarCount <= 0) return@change
            val origin = mapVisitTracker.activeMapVisit ?: return@change
            emit(
                MonitoringEventNames.MAP_STARS_VISIBLE,
                origin,
                mapOf("visible_star_count" to visibleStarCount),
                "map_stars_visible:${origin.mapVisitId}",
            )
        }

    fun starSelected(entrySource: String) =
        change {
            val mapOrigin = mapVisitTracker.activeMapVisit ?: return@change
            finishSelection("superseded")
            val origin = mapVisitTracker.selectStar(mapOrigin, entrySource)
            emit(MonitoringEventNames.STAR_SELECTED, origin, mapOf("entry_source" to entrySource), null)
            updateContext()
        }

    fun starDetailShown() =
        change {
            val origin = mapVisitTracker.activeSelection ?: return@change
            val source = mapVisitTracker.activeSelectionSource ?: return@change
            emit(
                MonitoringEventNames.STAR_DETAIL_SHOWN,
                origin.copy(state = "star_detail"),
                mapOf("entry_source" to source),
                "star_detail_shown:${origin.selectionId}",
            )
            mapVisitTracker.clearSelection()
            updateContext()
        }

    fun starDetailFailed(
        reason: String,
        errorCode: String?,
    ) = change { finishSelection(reason, errorCode) }

    fun savedStarVisible() =
        change {
            val observation = saveTracker.pendingSavedStar ?: return@change
            val mapOrigin = mapVisitTracker.activeMapVisit ?: return@change
            emit(
                MonitoringEventNames.SIGH_SAVED_STAR_VISIBLE,
                observation.origin.copy(
                    mapVisitId = mapOrigin.mapVisitId,
                    screen = "map",
                    state = "star_visible",
                ),
                mapOf(
                    "save_to_star_visible_ms" to (elapsed() - observation.startedAt).coerceAtLeast(0L),
                    "foreground_continuous" to observation.foregroundContinuous,
                ),
                "saved_star_visible:${observation.origin.sighAttemptId}",
            )
            saveTracker.clearPendingSavedStar()
        }

    fun mapVisitEnded(
        reason: String,
        quality: String,
    ) = change {
        val origin = mapVisitTracker.activeMapVisit ?: return@change
        finishSelection("map_closed")
        emit(
            MonitoringEventNames.MAP_VISIT_ENDED,
            origin.copy(state = "hidden"),
            mapOf("reason" to reason, "end_time_quality" to quality),
            "map_visit_end:${origin.mapVisitId}",
        )
        mapVisitTracker.clearVisit()
        updateContext()
    }

    private fun finishSelection(
        reason: String,
        errorCode: String? = null,
    ) {
        val origin = mapVisitTracker.activeSelection ?: return
        val source = mapVisitTracker.activeSelectionSource ?: return
        emit(
            MonitoringEventNames.STAR_DETAIL_FAILED,
            origin.copy(state = "star_detail_failed"),
            buildMap {
                put("entry_source", source)
                put("reason", reason)
                errorCode?.let { put("error_code", it) }
            },
            "star_detail_failed:${origin.selectionId}",
        )
        mapVisitTracker.clearSelection()
    }
}
