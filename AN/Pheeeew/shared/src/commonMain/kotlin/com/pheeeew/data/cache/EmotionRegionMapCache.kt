package com.pheeeew.data.cache

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.coversRegionViewport
import kotlin.time.Clock

/** Region membership depends on administrative boundaries, not the returned display points. */
internal class EmotionRegionMapCache(
    private val maxRegions: Int = 4,
    private val ttlMillis: Long = 180_000L,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private data class Key(
        val bounds: EmotionMapBounds,
        val level: EmotionRegionLevel,
        val groupId: String?,
    )

    private data class Entry(
        val regions: List<EmotionRegion>,
        val fetchedAt: Long,
    )

    private val entries = linkedMapOf<Key, Entry>()

    init {
        require(maxRegions > 0)
        require(ttlMillis > 0)
    }

    fun snapshot(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        groupId: String?,
    ): List<EmotionRegion>? {
        evictExpired()
        // Keep the complete summary: a display point need not lie inside its region's viewport.
        val key =
            entries.keys.lastOrNull {
                it.level == level && it.groupId == groupId && it.bounds.coversRegionViewport(bounds)
            } ?: return null
        val entry = entries.remove(key) ?: return null
        entries[key] = entry
        return entry.regions
    }

    fun put(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        groupId: String?,
        regions: List<EmotionRegion>,
    ) {
        evictExpired()
        val key = Key(bounds, level, groupId)
        entries.remove(key)
        entries[key] = Entry(regions.toList(), nowMillis())
        // Zooming between levels must not evict all entries from the other level.
        while (entries.keys.count { it.level == level } > maxRegions) {
            entries.remove(entries.keys.first { it.level == level })
        }
    }

    fun invalidate(
        level: EmotionRegionLevel,
        groupId: String?,
    ) {
        // Refresh may change totals shared by several cached viewports.
        entries.keys.removeAll { it.level == level && it.groupId == groupId }
    }

    fun invalidateGroup(groupId: String?) {
        entries.keys.removeAll { it.groupId == groupId }
    }

    private fun evictExpired() {
        val now = nowMillis()
        entries.entries.removeAll { now >= it.value.fetchedAt && now - it.value.fetchedAt >= ttlMillis }
    }
}
