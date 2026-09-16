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
private const val WORLD_LONGITUDE_SPAN = MAX_LONGITUDE - MIN_LONGITUDE
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

    fun removeSigh(sighId: Long) {
        cachedRegions.forEach { region ->
            region.sighs = region.sighs.filterNot { it.id == sighId }
        }
    }

    fun remove(id: Long) {
        removeSigh(id)
    }

    fun clear() {
        cachedRegions.clear()
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
    var sighs: List<SighPin>,
    val fetchedAtMillis: Long,
    var lastAccessOrder: Long,
)

internal fun SighBounds.expandForPrefetch(): SighBounds {
    val longitudeSpan = longitudeSpan()
    if (longitudeSpan >= WORLD_LONGITUDE_SPAN) {
        return copy(
            minLongitude = MIN_LONGITUDE,
            maxLongitude = MAX_LONGITUDE,
        )
    }

    val longitudePadding = longitudeSpan * PREFETCH_PADDING_RATIO
    val latitudePadding = (maxLatitude - minLatitude) * PREFETCH_PADDING_RATIO
    val expandedMinLongitude = minLongitude - longitudePadding
    val expandedMaxLongitude = minLongitude + longitudeSpan + longitudePadding
    val expandedLongitudeSpan = expandedMaxLongitude - expandedMinLongitude

    if (expandedLongitudeSpan >= WORLD_LONGITUDE_SPAN) {
        return SighBounds(
            minLongitude = MIN_LONGITUDE,
            minLatitude = (minLatitude - latitudePadding).coerceAtLeast(MIN_LATITUDE),
            maxLongitude = MAX_LONGITUDE,
            maxLatitude = (maxLatitude + latitudePadding).coerceAtMost(MAX_LATITUDE),
        )
    }

    return SighBounds(
        minLongitude = normalizeLongitude(expandedMinLongitude),
        minLatitude = (minLatitude - latitudePadding).coerceAtLeast(MIN_LATITUDE),
        maxLongitude = normalizeLongitude(expandedMaxLongitude),
        maxLatitude = (maxLatitude + latitudePadding).coerceAtMost(MAX_LATITUDE),
    )
}

private fun SighBounds.contains(other: SighBounds): Boolean =
    minLatitude <= other.minLatitude &&
        maxLatitude >= other.maxLatitude &&
        other.longitudeIntervals().all { otherInterval ->
            longitudeIntervals().any { interval -> interval.contains(otherInterval) }
        }

private fun SighBounds.intersects(other: SighBounds): Boolean =
    minLatitude <= other.maxLatitude &&
        maxLatitude >= other.minLatitude &&
        longitudeIntervals().any { interval ->
            other.longitudeIntervals().any(interval::intersects)
        }

private fun Coordinate.isInside(bounds: SighBounds): Boolean =
    bounds.longitudeIntervals().any { interval -> longitude in interval.min..interval.max } &&
        latitude in bounds.minLatitude..bounds.maxLatitude

private fun SighBounds.longitudeSpan(): Double =
    if (minLongitude <= maxLongitude) {
        maxLongitude - minLongitude
    } else {
        WORLD_LONGITUDE_SPAN - (minLongitude - maxLongitude)
    }

private fun SighBounds.longitudeIntervals(): List<LongitudeInterval> =
    if (minLongitude <= maxLongitude) {
        listOf(LongitudeInterval(minLongitude, maxLongitude))
    } else {
        listOf(
            LongitudeInterval(minLongitude, MAX_LONGITUDE),
            LongitudeInterval(MIN_LONGITUDE, maxLongitude),
        )
    }

private fun normalizeLongitude(longitude: Double): Double {
    val normalized =
        ((longitude - MIN_LONGITUDE) % WORLD_LONGITUDE_SPAN + WORLD_LONGITUDE_SPAN) % WORLD_LONGITUDE_SPAN +
            MIN_LONGITUDE
    return if (normalized == MIN_LONGITUDE && longitude > 0.0) MAX_LONGITUDE else normalized
}

private data class LongitudeInterval(
    val min: Double,
    val max: Double,
) {
    fun contains(other: LongitudeInterval): Boolean = min <= other.min && max >= other.max

    fun intersects(other: LongitudeInterval): Boolean = min <= other.max && max >= other.min
}
