package com.pheeeew.data.cache

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmotionRegionMapCacheTest {
    private val bounds = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
    // The server may return display points outside the viewport.
    private val regions = listOf(EmotionRegion("a", "서울", 127.5, 37.8, 3, null))

    @Test
    fun `동일 영역 계층 그룹만 재사용하고 화면 밖 표시점도 보관한다`() {
        val cache = EmotionRegionMapCache()
        cache.put(bounds, EmotionRegionLevel.SIDO, null, regions)
        assertEquals(regions, cache.snapshot(bounds, EmotionRegionLevel.SIDO, null))
        assertNull(cache.snapshot(bounds.copy(maxLongitude = 127.0), EmotionRegionLevel.SIDO, null))
        assertNull(cache.snapshot(bounds, EmotionRegionLevel.EMD, null))
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
        val areas = (0..4).map { bounds.copy(minLongitude = 126.0 + it * 0.01) }
        areas.take(4).forEach { cache.put(it, EmotionRegionLevel.SIDO, null, regions) }
        cache.snapshot(areas[0], EmotionRegionLevel.SIDO, null)
        cache.put(areas[4], EmotionRegionLevel.SIDO, null, regions)
        assertNull(cache.snapshot(areas[1], EmotionRegionLevel.SIDO, null))
        assertEquals(regions, cache.snapshot(areas[0], EmotionRegionLevel.SIDO, null))
    }

    @Test
    fun `새로고침은 같은 계층 그룹의 영역들을 무효화한다`() {
        val cache = EmotionRegionMapCache()
        cache.put(bounds, EmotionRegionLevel.SIDO, null, regions)
        cache.put(bounds.copy(minLongitude = 126.8), EmotionRegionLevel.SIDO, null, regions)
        cache.put(bounds, EmotionRegionLevel.EMD, null, regions)
        cache.invalidate(EmotionRegionLevel.SIDO, null)
        assertNull(cache.snapshot(bounds, EmotionRegionLevel.SIDO, null))
        assertNull(cache.snapshot(bounds.copy(minLongitude = 126.8), EmotionRegionLevel.SIDO, null))
        assertEquals(regions, cache.snapshot(bounds, EmotionRegionLevel.EMD, null))
    }
}
