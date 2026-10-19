package com.pheeeew.data.cache

import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionQueryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

/** Region membership depends on administrative boundaries, not the returned display points. */
internal class EmotionRegionMapCache(
    private val maxRegions: Int = 4,
    private val ttlMillis: Long = 180_000L,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    data class Generation(
        val session: Long,
        val group: Long,
    )

    private data class Entry(
        val key: EmotionRegionQueryKey,
        val regions: List<EmotionRegion>,
        val fetchedAt: Long,
    )

    private data class State(
        val sessionGeneration: Long = 0L,
        val groupGenerations: Map<String?, Long> = emptyMap(),
        // Keep recency in a list: Map equality ignores insertion order, which would
        // otherwise make StateFlow discard LRU-only updates.
        val entries: List<Entry> = emptyList(),
    )

    private val state = MutableStateFlow(State())

    init {
        require(maxRegions > 0)
        require(ttlMillis > 0)
    }

    fun snapshot(key: EmotionRegionQueryKey): List<EmotionRegion>? {
        while (true) {
            val current = state.value
            val now = nowMillis()
            val live = current.entries.filterNot { it.isExpired(now) }
            val entry = live.firstOrNull { it.key == key }
            val nextEntries = if (entry == null) live else live.filterNot { it.key == key } + entry
            if (nextEntries == current.entries || state.compareAndSet(current, current.copy(entries = nextEntries))) {
                return entry?.regions?.toList()
            }
        }
    }

    fun put(
        key: EmotionRegionQueryKey,
        regions: List<EmotionRegion>,
        generation: Generation = generation(key.groupId),
        fetchedAt: Long = nowMillis(),
    ) {
        val entry = Entry(key, regions.toList(), fetchedAt)
        while (true) {
            val current = state.value
            if (current.generation(key.groupId) != generation) return
            val next = current.entries.filterNot { it.isExpired(entry.fetchedAt) || it.key == key }.toMutableList()
            next += entry
            while (next.count { it.key.level == key.level } > maxRegions) {
                next.removeAt(next.indexOfFirst { it.key.level == key.level })
            }
            if (state.compareAndSet(current, current.copy(entries = next))) return
        }
    }

    fun clear(): Long = invalidateAll()

    fun generation(groupId: String?): Generation = state.value.generation(groupId)

    fun invalidateGroup(groupId: String?): Generation {
        while (true) {
            val current = state.value
            val next =
                current.copy(
                    groupGenerations =
                        current.groupGenerations + (groupId to ((current.groupGenerations[groupId] ?: 0L) + 1L)),
                    entries = current.entries.filterNot { it.key.groupId == groupId },
                )
            if (state.compareAndSet(current, next)) return next.generation(groupId)
        }
    }

    fun invalidateAll(): Long {
        while (true) {
            val current = state.value
            val next = State(sessionGeneration = current.sessionGeneration + 1L)
            if (state.compareAndSet(current, next)) return next.sessionGeneration
        }
    }

    private fun State.generation(groupId: String?): Generation =
        Generation(sessionGeneration, groupGenerations[groupId] ?: 0L)

    private fun Entry.isExpired(now: Long): Boolean = now >= fetchedAt && now - fetchedAt >= ttlMillis
}
