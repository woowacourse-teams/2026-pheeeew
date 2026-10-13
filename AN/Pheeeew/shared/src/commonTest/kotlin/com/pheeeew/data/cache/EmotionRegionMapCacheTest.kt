package com.pheeeew.data.cache

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterPeriod
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmotionRegionMapCacheTest {
    @Test
    fun `날짜 내용 범위 그룹이 다른 요약은 섞이지 않고 새로고침은 모든 필터를 무효화한다`() {
        val cache = EmotionRegionMapCache(maxRegions = 8)
        val filters =
            listOf(
                MapFilterSettings(),
                MapFilterSettings(period = MapFilterPeriod.ALL),
                MapFilterSettings(content = MapFilterContent.MEMO),
                MapFilterSettings(content = MapFilterContent.AUDIO),
                MapFilterSettings(scope = MapFilterScope.MY),
                MapFilterSettings(scope = MapFilterScope.GROUP, groupId = "a"),
                MapFilterSettings(scope = MapFilterScope.GROUP, groupId = "b"),
            ).map { it.toSearchFilter() }
        filters.forEachIndexed { index, filter ->
            assertNull(cache.snapshot(bounds, EmotionRegionLevel.SIDO, filter.groupId, filter))
            cache.put(
                bounds,
                EmotionRegionLevel.SIDO,
                filter.groupId,
                regions.map { it.copy(count = index + 1L) },
                filter,
            )
        }
        filters.forEachIndexed { index, filter ->
            assertEquals(
                index + 1L,
                cache.snapshot(bounds, EmotionRegionLevel.SIDO, filter.groupId, filter)?.single()?.count,
            )
        }
        cache.invalidateGroup(null)
        filters.filter { it.groupId == null }.forEach { filter ->
            assertNull(cache.snapshot(bounds, EmotionRegionLevel.SIDO, null, filter))
        }
        assertEquals(6L, cache.snapshot(bounds, EmotionRegionLevel.SIDO, "a", filters[5])?.single()?.count)
        assertEquals(7L, cache.snapshot(bounds, EmotionRegionLevel.SIDO, "b", filters[6])?.single()?.count)
    }

    private val bounds = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)

    // The server may return display points outside the viewport.
    private val regions = listOf(EmotionRegion("a", "서울", 127.5, 37.8, 3, null))

    @Test
    fun `조회 범위 안의 화면은 같은 계층 그룹의 전체 요약을 재사용한다`() {
        val cache = EmotionRegionMapCache()
        cache.put(bounds, EmotionRegionLevel.SIDO, null, regions)
        assertEquals(regions, cache.snapshot(bounds, EmotionRegionLevel.SIDO, null))
        assertEquals(regions, cache.snapshot(bounds.copy(maxLongitude = 127.0), EmotionRegionLevel.SIDO, null))
        assertNull(cache.snapshot(bounds.copy(maxLongitude = 127.2), EmotionRegionLevel.SIDO, null))
        assertNull(cache.snapshot(bounds, EmotionRegionLevel.SIGUNGU, null))
        assertNull(cache.snapshot(bounds, EmotionRegionLevel.SIDO, "group"))
    }

    @Test
    fun `빈 결과를 보관하고 접근해도 3분 만료는 연장하지 않는다`() {
        var now = 0L
        val cache = EmotionRegionMapCache(nowMillis = { now })
        cache.put(bounds, EmotionRegionLevel.SIDO, null, emptyList())
        now = 179_999L
        assertEquals(emptyList(), cache.snapshot(bounds, EmotionRegionLevel.SIDO, null))
        now = 180_000L
        assertNull(cache.snapshot(bounds, EmotionRegionLevel.SIDO, null))
    }

    @Test
    fun `최근 사용한 4개 영역만 유지한다`() {
        val cache = EmotionRegionMapCache()
        val areas = (0..4).map { bounds.copy(minLongitude = 120.0 + it, maxLongitude = 120.2 + it) }
        areas.take(4).forEach { cache.put(it, EmotionRegionLevel.SIDO, null, regions) }
        cache.snapshot(areas[0], EmotionRegionLevel.SIDO, null)
        cache.put(areas[4], EmotionRegionLevel.SIDO, null, regions)
        assertNull(cache.snapshot(areas[1], EmotionRegionLevel.SIDO, null))
        assertEquals(regions, cache.snapshot(areas[0], EmotionRegionLevel.SIDO, null))
    }

    @Test
    fun `다른 계층의 조회가 기존 계층 캐시를 밀어내지 않는다`() {
        val cache = EmotionRegionMapCache()
        cache.put(bounds, EmotionRegionLevel.SIDO, null, regions)
        (0..5).forEach { offset ->
            cache.put(bounds.copy(minLongitude = 126.0 + offset * 0.01), EmotionRegionLevel.SIGUNGU, null, regions)
        }
        assertEquals(regions, cache.snapshot(bounds, EmotionRegionLevel.SIDO, null))
    }

    @Test
    fun `부분적으로 겹치는 영역을 완전한 캐시로 사용하지 않는다`() {
        val cache = EmotionRegionMapCache()
        cache.put(bounds, EmotionRegionLevel.SIDO, null, regions)
        assertNull(cache.snapshot(bounds.copy(minLongitude = 126.8), EmotionRegionLevel.SIDO, null))
    }

    @Test
    fun `날짜 변경선을 가로지르는 조회 범위도 포함 여부를 확인한다`() {
        val cache = EmotionRegionMapCache()
        cache.put(EmotionMapBounds(170.0, -20.0, -170.0, 20.0), EmotionRegionLevel.SIDO, null, regions)
        assertEquals(
            regions,
            cache.snapshot(EmotionMapBounds(175.0, -10.0, -175.0, 10.0), EmotionRegionLevel.SIDO, null),
        )
        assertEquals(
            regions,
            cache.snapshot(EmotionMapBounds(-179.0, -10.0, -175.0, 10.0), EmotionRegionLevel.SIDO, null),
        )
        assertNull(cache.snapshot(EmotionMapBounds(-10.0, -10.0, 10.0, 10.0), EmotionRegionLevel.SIDO, null))
    }

    @Test
    fun `새로고침은 같은 계층 그룹의 영역들을 무효화한다`() {
        val cache = EmotionRegionMapCache()
        cache.put(bounds, EmotionRegionLevel.SIDO, null, regions)
        cache.put(bounds.copy(minLongitude = 126.8), EmotionRegionLevel.SIDO, null, regions)
        cache.put(bounds, EmotionRegionLevel.SIGUNGU, null, regions)
        cache.invalidate(EmotionRegionLevel.SIDO, null)
        assertNull(cache.snapshot(bounds, EmotionRegionLevel.SIDO, null))
        assertNull(cache.snapshot(bounds.copy(minLongitude = 126.8), EmotionRegionLevel.SIDO, null))
        assertEquals(regions, cache.snapshot(bounds, EmotionRegionLevel.SIGUNGU, null))
    }
}
