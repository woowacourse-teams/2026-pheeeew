package com.pheeeew.legacy.core.monitoring

import com.pheeeew.legacy.core.monitoring.tracker.MapVisitTracker
import com.pheeeew.legacy.core.monitoring.tracker.SaveTracker
import com.pheeeew.legacy.core.monitoring.tracker.VisitTracker

/** Coordinates map visits, star selection, and the saved-star handoff. */
internal class MapVisitCoordinator(
    private val visitTracker: VisitTracker,
    private val mapVisitTracker: MapVisitTracker,
    private val saveTracker: SaveTracker,
    private val runtime: MonitoringRuntime,
) {
    fun mapVisitStarted(entryReason: String) =
        runtime.change {
            val sessionId = visitTracker.currentVisitId ?: return@change
            val origin = mapVisitTracker.startVisit(sessionId) ?: return@change
            runtime.emit(
                MonitoringEventNames.MAP_VISIT_STARTED,
                origin,
                mapOf("entry_reason" to entryReason),
                "map_visit_start:${origin.mapVisitId}",
            )
            runtime.updateContext()
        }

    fun mapStarsVisible(visibleStarCount: Int) =
        runtime.change {
            if (visibleStarCount <= 0) return@change
            val origin = mapVisitTracker.activeMapVisit ?: return@change
            runtime.emit(
                MonitoringEventNames.MAP_STARS_VISIBLE,
                origin,
                mapOf("visible_star_count" to visibleStarCount),
                "map_stars_visible:${origin.mapVisitId}",
            )
        }

    fun starSelected(entrySource: String) =
        runtime.change {
            val mapOrigin = mapVisitTracker.activeMapVisit ?: return@change
            finishSelection("superseded")
            val origin = mapVisitTracker.selectStar(mapOrigin, entrySource)
            runtime.emit(MonitoringEventNames.STAR_SELECTED, origin, mapOf("entry_source" to entrySource), null)
            runtime.updateContext()
        }

    fun starDetailShown() =
        runtime.change {
            val origin = mapVisitTracker.activeSelection ?: return@change
            val source = mapVisitTracker.activeSelectionSource ?: return@change
            runtime.emit(
                MonitoringEventNames.STAR_DETAIL_SHOWN,
                origin.copy(state = "star_detail"),
                mapOf("entry_source" to source),
                "star_detail_shown:${origin.selectionId}",
            )
            mapVisitTracker.clearSelection()
            runtime.updateContext()
        }

    fun starDetailFailed(
        reason: String,
        errorCode: String?,
    ) = runtime.change { finishSelection(reason, errorCode) }

    fun savedStarVisible() =
        runtime.change {
            val observation = saveTracker.pendingSavedStar ?: return@change
            val mapOrigin = mapVisitTracker.activeMapVisit ?: return@change
            runtime.emit(
                MonitoringEventNames.SIGH_SAVED_STAR_VISIBLE,
                observation.origin.copy(
                    mapVisitId = mapOrigin.mapVisitId,
                    screen = "map",
                    state = "star_visible",
                ),
                mapOf(
                    "save_to_star_visible_ms" to (runtime.elapsed() - observation.startedAt).coerceAtLeast(0L),
                    "foreground_continuous" to observation.foregroundContinuous,
                ),
                "saved_star_visible:${observation.origin.sighAttemptId}",
            )
            saveTracker.clearPendingSavedStar()
        }

    fun mapVisitEnded(
        reason: String,
        quality: String,
    ) = runtime.change {
        val origin = mapVisitTracker.activeMapVisit ?: return@change
        finishSelection("map_closed")
        runtime.emit(
            MonitoringEventNames.MAP_VISIT_ENDED,
            origin.copy(state = "hidden"),
            mapOf("reason" to reason, "end_time_quality" to quality),
            "map_visit_end:${origin.mapVisitId}",
        )
        mapVisitTracker.clearVisit()
        runtime.updateContext()
    }

    private fun finishSelection(
        reason: String,
        errorCode: String? = null,
    ) {
        val origin = mapVisitTracker.activeSelection ?: return
        val source = mapVisitTracker.activeSelectionSource ?: return
        runtime.emit(
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
