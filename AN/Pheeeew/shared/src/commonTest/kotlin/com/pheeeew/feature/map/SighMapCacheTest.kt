@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.map

import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SighMapCacheTest {
    private val viewport =
        SighBounds(
            minLongitude = 126.9,
            minLatitude = 37.5,
            maxLongitude = 127.0,
            maxLatitude = 37.6,
        )

    @Test
    fun `조회 범위는 viewport 너비와 높이의 절반만큼 확장한다`() {
        val expandedBounds = viewport.expandForPrefetch()

        assertEquals(126.85, expandedBounds.minLongitude, absoluteTolerance = 0.0000001)
        assertEquals(37.45, expandedBounds.minLatitude, absoluteTolerance = 0.0000001)
        assertEquals(127.05, expandedBounds.maxLongitude, absoluteTolerance = 0.0000001)
        assertEquals(37.65, expandedBounds.maxLatitude, absoluteTolerance = 0.0000001)
    }

    @Test
    fun `날짜변경선을 넘는 viewport도 원래 경도를 유지한 채 확장한다`() {
        val wrappedViewport =
            SighBounds(
                minLongitude = 170.0,
                minLatitude = -10.0,
                maxLongitude = -170.0,
                maxLatitude = 10.0,
            )

        val expandedBounds = wrappedViewport.expandForPrefetch()

        assertEquals(160.0, expandedBounds.minLongitude, absoluteTolerance = 0.0000001)
        assertEquals(-160.0, expandedBounds.maxLongitude, absoluteTolerance = 0.0000001)
    }

    @Test
    fun `확장 범위는 위도와 경도 한계를 넘지 않는다`() {
        val edgeBounds =
            SighBounds(
                minLongitude = -179.0,
                minLatitude = -89.0,
                maxLongitude = 179.0,
                maxLatitude = 89.0,
            )

        assertEquals(
            SighBounds(
                minLongitude = -180.0,
                minLatitude = -90.0,
                maxLongitude = 180.0,
                maxLatitude = 90.0,
            ),
            edgeBounds.expandForPrefetch(),
        )
    }

    @Test
    fun `빈 응답도 조회 완료 영역으로 저장한다`() {
        val cache = SighMapCache(currentTimeMillis = { 0L })
        val queryBounds = viewport.expandForPrefetch()

        cache.put(queryBounds, emptyList())

        assertTrue(cache.covers(viewport))
        assertEquals(emptyList(), cache.visibleSighs(viewport))
    }

    @Test
    fun `현재 viewport 안에 있는 별만 반환한다`() {
        val cache = SighMapCache(currentTimeMillis = { 0L })
        val visiblePin = SighPin(id = 1L, coordinate = Coordinate(latitude = 37.55, longitude = 126.95))
        val prefetchedPin = SighPin(id = 2L, coordinate = Coordinate(latitude = 37.64, longitude = 127.04))

        cache.put(viewport.expandForPrefetch(), listOf(visiblePin, prefetchedPin))

        assertEquals(listOf(visiblePin), cache.visibleSighs(viewport))
    }

    @Test
    fun `날짜변경선을 넘는 캐시 범위는 양쪽 끝의 별을 반환한다`() {
        val wrappedViewport =
            SighBounds(
                minLongitude = 170.0,
                minLatitude = -10.0,
                maxLongitude = -170.0,
                maxLatitude = 10.0,
            )
        val eastPin = SighPin(id = 1L, coordinate = Coordinate(latitude = 0.0, longitude = 175.0))
        val westPin = SighPin(id = 2L, coordinate = Coordinate(latitude = 0.0, longitude = -175.0))
        val middlePin = SighPin(id = 3L, coordinate = Coordinate(latitude = 0.0, longitude = 0.0))
        val cache = SighMapCache(currentTimeMillis = { 0L })

        cache.put(wrappedViewport.expandForPrefetch(), listOf(eastPin, westPin, middlePin))

        assertTrue(cache.covers(wrappedViewport))
        assertEquals(listOf(eastPin, westPin), cache.visibleSighs(wrappedViewport))
    }

    @Test
    fun `TTL이 지난 영역은 조회 완료 영역으로 사용하지 않는다`() {
        var nowMillis = 0L
        val cache =
            SighMapCache(
                ttlMillis = 1_000L,
                currentTimeMillis = { nowMillis },
            )
        cache.put(viewport.expandForPrefetch(), emptyList())

        nowMillis = 999L
        assertTrue(cache.covers(viewport))

        nowMillis = 1_000L
        assertFalse(cache.covers(viewport))
        assertEquals(0, cache.regionCount)
    }

    @Test
    fun `만료 확인 전에는 기존 별을 stale 데이터로 표시할 수 있다`() {
        var nowMillis = 0L
        val cache =
            SighMapCache(
                ttlMillis = 1_000L,
                currentTimeMillis = { nowMillis },
            )
        val sighPin = SighPin(id = 1L, coordinate = Coordinate(latitude = 37.55, longitude = 126.95))
        cache.put(viewport.expandForPrefetch(), listOf(sighPin))

        nowMillis = 1_000L

        assertEquals(listOf(sighPin), cache.visibleSighs(viewport))
        assertFalse(cache.covers(viewport))
    }

    @Test
    fun `영역 한도를 넘으면 가장 오래 사용하지 않은 영역을 제거한다`() {
        var nowMillis = 0L
        val cache =
            SighMapCache(
                maxRegions = 2,
                currentTimeMillis = { nowMillis++ },
            )
        val firstRegion = viewport.expandForPrefetch()
        val secondViewport = viewport.shiftLongitude(1.0)
        val secondRegion = secondViewport.expandForPrefetch()
        val thirdViewport = viewport.shiftLongitude(2.0)
        val thirdRegion = thirdViewport.expandForPrefetch()
        cache.put(firstRegion, emptyList())
        cache.put(secondRegion, emptyList())
        assertTrue(cache.covers(viewport))

        cache.put(thirdRegion, emptyList())

        assertTrue(cache.covers(viewport))
        assertFalse(cache.covers(secondViewport))
        assertTrue(cache.covers(thirdViewport))
        assertEquals(2, cache.regionCount)
    }
}

private fun SighBounds.shiftLongitude(delta: Double): SighBounds =
    copy(
        minLongitude = minLongitude + delta,
        maxLongitude = maxLongitude + delta,
    )
