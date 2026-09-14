package com.pheeeew.feature.map

import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import kotlin.time.Clock

internal const val MAP_SIGH_QUERY_DEBOUNCE_MILLIS = 700L

private const val DEFAULT_MAX_CACHED_REGIONS = 4
private const val DEFAULT_CACHE_TTL_MILLIS = 3 * 60 * 1_000L
private const val PREFETCH_PADDING_RATIO = 0.5
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0

internal class SighMapCache(
    private val maxRegions: Int = DEFAULT_MAX_CACHED_REGIONS,
    private val ttlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
    private val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val cachedRegions = mutableListOf<CachedRegion>()
    private var nextAccessOrder = 0L

    init {
        require(maxRegions > 0)
        require(ttlMillis > 0L)
    }

    fun covers(bounds: SighBounds): Boolean {
        val nowMillis = currentTimeMillis()
        evictExpired(nowMillis)
        val cachedRegion =
            cachedRegions
                .filter { it.queryBounds.contains(bounds) }
                .maxByOrNull(CachedRegion::fetchedAtMillis)
                ?: return false

        cachedRegion.lastAccessOrder = nextAccessOrder++
        return true
    }

    fun put(
        queryBounds: SighBounds,
        sighs: List<SighPin>,
    ) {
        val nowMillis = currentTimeMillis()
        evictExpired(nowMillis)
        cachedRegions.removeAll { it.queryBounds == queryBounds }
        cachedRegions +=
            CachedRegion(
                queryBounds = queryBounds,
                sighs = sighs.distinctBy(SighPin::id),
                fetchedAtMillis = nowMillis,
                lastAccessOrder = nextAccessOrder++,
            )
        evictLeastRecentlyUsed()
    }

    fun visibleSighs(bounds: SighBounds): List<SighPin> {
        val relevantRegions =
            cachedRegions
                .filter { it.queryBounds.intersects(bounds) }
                .sortedByDescending(CachedRegion::fetchedAtMillis)

        relevantRegions.forEach { it.lastAccessOrder = nextAccessOrder++ }

        return relevantRegions
            .asSequence()
            .flatMap { it.sighs.asSequence() }
            .filter { it.coordinate.isInside(bounds) }
            .distinctBy(SighPin::id)
            .sortedBy(SighPin::id)
            .toList()
    }

    internal val regionCount: Int
        get() = cachedRegions.size

    private fun evictExpired(nowMillis: Long) {
        cachedRegions.removeAll { cachedRegion ->
            nowMillis >= cachedRegion.fetchedAtMillis &&
                nowMillis - cachedRegion.fetchedAtMillis >= ttlMillis
        }
    }

    private fun evictLeastRecentlyUsed() {
        while (cachedRegions.size > maxRegions) {
            val leastRecentlyUsed = cachedRegions.minBy(CachedRegion::lastAccessOrder)
            cachedRegions.remove(leastRecentlyUsed)
        }
    }
}

private data class CachedRegion(
    val queryBounds: SighBounds,
    val sighs: List<SighPin>,
    val fetchedAtMillis: Long,
    var lastAccessOrder: Long,
)

internal fun SighBounds.expandForPrefetch(): SighBounds {
    val longitudePadding = (maxLongitude - minLongitude) * PREFETCH_PADDING_RATIO
    val latitudePadding = (maxLatitude - minLatitude) * PREFETCH_PADDING_RATIO

    return SighBounds(
        minLongitude = (minLongitude - longitudePadding).coerceAtLeast(MIN_LONGITUDE),
        minLatitude = (minLatitude - latitudePadding).coerceAtLeast(MIN_LATITUDE),
        maxLongitude = (maxLongitude + longitudePadding).coerceAtMost(MAX_LONGITUDE),
        maxLatitude = (maxLatitude + latitudePadding).coerceAtMost(MAX_LATITUDE),
    )
}

private fun SighBounds.contains(other: SighBounds): Boolean =
    minLongitude <= other.minLongitude &&
        minLatitude <= other.minLatitude &&
        maxLongitude >= other.maxLongitude &&
        maxLatitude >= other.maxLatitude

private fun SighBounds.intersects(other: SighBounds): Boolean =
    minLongitude <= other.maxLongitude &&
        maxLongitude >= other.minLongitude &&
        minLatitude <= other.maxLatitude &&
        maxLatitude >= other.minLatitude

private fun Coordinate.isInside(bounds: SighBounds): Boolean =
    longitude in bounds.minLongitude..bounds.maxLongitude &&
        latitude in bounds.minLatitude..bounds.maxLatitude
