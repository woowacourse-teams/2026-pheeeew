package com.pheeeew.data.cache

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPage
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import kotlin.time.Clock

/** Caches viewport responses while keeping page assembly and expiry out of the feature layer. */
internal class EmotionMapCache(
    private val maxRegions: Int = 4,
    private val ttlMillis: Long = 180_000L,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val regions = mutableListOf<Region>()
    private var accessOrder = 0L

    init {
        require(maxRegions > 0)
        require(ttlMillis > 0)
    }

    fun clear() {
        regions.clear()
    }

    fun invalidate(
        bounds: EmotionMapBounds,
        groupId: String?,
    ) {
        regions.removeAll { it.groupId == groupId && it.bounds.intersects(bounds) }
    }

    fun completePage(
        bounds: EmotionMapBounds,
        groupId: String?,
    ): EmotionMapPage? {
        evictExpired()
        val region =
            regions.lastOrNull {
                it.isComplete && it.groupId == groupId && it.bounds.contains(bounds)
            } ?: return null
        region.lastAccess = accessOrder++
        return region.page.copy(pins = region.page.pins.filter { bounds.contains(it) })
    }

    fun snapshot(
        bounds: EmotionMapBounds,
        groupId: String?,
    ): EmotionMapSnapshot? {
        evictExpired()
        val relevant = regions.asReversed().filter { it.groupId == groupId && it.bounds.intersects(bounds) }
        if (relevant.isEmpty()) return null
        relevant.forEach { it.lastAccess = accessOrder++ }
        return EmotionMapSnapshot(
            pins = relevant.flatMap { it.page.pins }.filter { bounds.contains(it) }.distinctBy { it.id },
        )
    }

    fun appendPage(
        bounds: EmotionMapBounds,
        groupId: String?,
        cursor: String?,
        page: EmotionMapPage,
    ) {
        evictExpired()
        val previous =
            cursor?.let { expectedCursor ->
                regions.lastOrNull {
                    it.bounds == bounds && it.groupId == groupId && it.page.nextCursor == expectedCursor
                }
            }
        val isFirstPage = cursor == null || previous?.hasFirstPage == true
        val allPins =
            (previous?.page?.pins.orEmpty() + page.pins)
                .distinctBy(EmotionMapPin::id)
        val accumulatedPage =
            EmotionMapPage(
                pins = allPins,
                hasNext = page.hasNext,
                nextCursor = page.nextCursor,
                invalidItemCount = (previous?.page?.invalidItemCount ?: 0) + page.invalidItemCount,
            )
        // An older overlapping response no longer proves that the region is complete.
        // Keep its pins for snapshots, but fetch again before replacing newer pins.
        regions
            .filter { it.groupId == groupId && it.bounds.intersects(bounds) }
            .forEach { it.isComplete = false }
        regions.removeAll { it.bounds == bounds && it.groupId == groupId }
        regions +=
            Region(
                bounds = bounds,
                groupId = groupId,
                page = accumulatedPage,
                hasFirstPage = isFirstPage,
                isComplete = isFirstPage && !page.hasNext,
                fetchedAt = nowMillis(),
                lastAccess = accessOrder++,
            )
        while (regions.size > maxRegions) {
            regions.remove(regions.minBy(Region::lastAccess))
        }
    }

    private fun evictExpired() {
        val now = nowMillis()
        regions.removeAll { now >= it.fetchedAt && now - it.fetchedAt >= ttlMillis }
    }

    private data class Region(
        val bounds: EmotionMapBounds,
        val groupId: String?,
        val page: EmotionMapPage,
        val hasFirstPage: Boolean,
        var isComplete: Boolean,
        val fetchedAt: Long,
        var lastAccess: Long,
    )
}

private fun EmotionMapBounds.contains(pin: EmotionMapPin): Boolean =
    pin.latitude in minLatitude..maxLatitude &&
        longitudeIntervals().any { pin.longitude in it }

private fun EmotionMapBounds.contains(other: EmotionMapBounds): Boolean =
    minLatitude <= other.minLatitude && maxLatitude >= other.maxLatitude &&
        other.longitudeIntervals().all { otherInterval ->
            longitudeIntervals().any {
                it.start <= otherInterval.start && it.endInclusive >= otherInterval.endInclusive
            }
        }

private fun EmotionMapBounds.intersects(other: EmotionMapBounds): Boolean =
    minLatitude <= other.maxLatitude && maxLatitude >= other.minLatitude &&
        longitudeIntervals().any { interval ->
            other.longitudeIntervals().any { interval.start <= it.endInclusive && interval.endInclusive >= it.start }
        }

private fun EmotionMapBounds.longitudeIntervals(): List<ClosedFloatingPointRange<Double>> =
    if (minLongitude <= maxLongitude) {
        listOf(minLongitude..maxLongitude)
    } else {
        listOf(minLongitude..180.0, -180.0..maxLongitude)
    }
