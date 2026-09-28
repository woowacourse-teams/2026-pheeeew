package com.pheeeew.data.cache

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPage
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EmotionMapCacheTest {
    @Test
    fun `완료된 캐시는 포함 영역에 재사용하고 유효기간이 지나면 조회하지 않는다`() {
        var now = 0L
        val cache = EmotionMapCache(nowMillis = { now })
        val outer = EmotionMapBounds(127.0, 37.0, 128.0, 38.0)
        val inner = EmotionMapBounds(127.1, 37.1, 127.3, 37.3)
        val pins = listOf(pin(1, 127.2), pin(2, 127.8))
        cache.appendPage(outer, null, null, EmotionMapPage(pins, false, null, 0))

        assertEquals(listOf(1L), cache.completePage(inner, null)?.pins?.map { it.id })
        now = 180_000L
        assertNull(cache.completePage(inner, null))
        assertNull(cache.snapshot(inner, null))
    }

    @Test
    fun `가장 오래 사용하지 않은 영역을 제거한다`() {
        val cache = EmotionMapCache(maxRegions = 2)
        val first = EmotionMapBounds(127.0, 37.0, 127.1, 38.0)
        val second = EmotionMapBounds(128.0, 37.0, 128.1, 38.0)
        val third = EmotionMapBounds(129.0, 37.0, 129.1, 38.0)
        val page = EmotionMapPage(emptyList(), false, null, 0)
        cache.appendPage(first, null, null, page)
        cache.appendPage(second, null, null, page)
        assertNotNull(cache.completePage(first, null))
        cache.appendPage(third, null, null, page)

        assertNull(cache.completePage(second, null))
        assertNotNull(cache.completePage(first, null))
        assertNotNull(cache.completePage(third, null))
    }

    @Test
    fun `부분 페이지는 날짜 변경선을 넘는 영역에서도 표시하되 조회를 대체하지 않는다`() {
        val cache = EmotionMapCache()
        val bounds = EmotionMapBounds(179.0, 37.0, -179.0, 38.0)
        val pins = listOf(pin(1, 179.5), pin(2, -179.5), pin(3, 127.0))
        cache.appendPage(bounds, null, null, EmotionMapPage(pins, true, "next", 0))

        assertEquals(listOf(1L, 2L), cache.snapshot(bounds, null)?.pins?.map { it.id })
        assertNull(cache.completePage(bounds, null))
    }

    @Test
    fun `새로고침은 겹치는 이전 영역을 제거해 등록 전 캐시가 다시 사용되지 않는다`() {
        val cache = EmotionMapCache()
        val outer = EmotionMapBounds(127.0, 37.0, 128.0, 38.0)
        val inner = EmotionMapBounds(127.1, 37.1, 127.3, 37.3)
        val distant = EmotionMapBounds(129.0, 37.0, 130.0, 38.0)
        val empty = EmotionMapPage(emptyList(), false, null, 0)
        cache.appendPage(outer, null, null, empty)
        cache.appendPage(outer, "group", null, empty)
        cache.appendPage(distant, null, null, empty)

        cache.invalidate(inner, null)
        cache.appendPage(inner, null, null, empty.copy(pins = listOf(pin(42, 127.2))))

        assertNull(cache.completePage(outer, null))
        assertEquals(listOf(42L), cache.snapshot(outer, null)?.pins?.map { it.id })
        assertEquals(listOf(42L), cache.completePage(inner, null)?.pins?.map { it.id })
        assertNotNull(cache.completePage(outer, "group"))
        assertNotNull(cache.completePage(distant, null))
    }

    private fun pin(
        id: Long,
        longitude: Double,
    ) = EmotionMapPin(id, longitude, 37.2, "2026-09-27T00:00:00Z", EmotionState.ANGRY, 0.0, null)
}
