package com.pheeeew.data.cache

import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionQueryKey
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterPeriod
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.model.emotion.SearchDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmotionRegionMapCacheTest {
    private fun key(
        level: EmotionRegionLevel = EmotionRegionLevel.SIDO,
        filter: EmotionSearchFilter? = null,
    ) = EmotionRegionQueryKey.create(level, filter)

    @Test
    fun `날짜 내용 범위가 다른 요약은 섞이지 않고 전체 무효화로 제거한다`() {
        val cache = EmotionRegionMapCache(maxRegions = 8)
        val filters =
            listOf(
                MapFilterSettings(),
                MapFilterSettings(period = MapFilterPeriod.ALL),
                MapFilterSettings(content = MapFilterContent.MEMO),
                MapFilterSettings(content = MapFilterContent.AUDIO),
                MapFilterSettings(scope = MapFilterScope.MY),
            ).map { it.toSearchFilter() }
        filters.forEachIndexed { index, filter ->
            val queryKey = key(filter = filter)
            assertNull(cache.snapshot(queryKey))
            cache.put(queryKey, regions.map { it.copy(count = index + 1L) })
        }
        filters.forEachIndexed { index, filter ->
            assertEquals(
                index + 1L,
                cache.snapshot(key(filter = filter))?.single()?.count,
            )
        }
        cache.invalidateAll()
        filters.forEach { filter ->
            assertNull(cache.snapshot(key(filter = filter)))
        }
    }

    // The server may return display points outside the viewport.
    private val regions = listOf(EmotionRegion("a", "서울", 127.5, 37.8, 3, null))

    @Test
    fun `전체 기간 생략값과 명시 기본값은 같은 조건 키를 만든다`() {
        val omitted = key()
        val explicitDefaults = key(filter = MapFilterSettings(period = MapFilterPeriod.ALL).toSearchFilter())
        assertEquals(omitted, explicitDefaults)
        assertEquals(
            MapFilterScope.MY,
            key(filter = MapFilterSettings(scope = MapFilterScope.MY).toSearchFilter()).scope,
        )
    }

    @Test
    fun `조회 범위 안의 화면은 같은 계층의 전체 요약을 재사용한다`() {
        val cache = EmotionRegionMapCache()
        cache.put(key(), regions)
        assertEquals(regions, cache.snapshot(key()))
        assertEquals(regions, cache.snapshot(key()))
        assertNull(cache.snapshot(key(level = EmotionRegionLevel.SIGUNGU)))
        assertNull(cache.snapshot(key(filter = MapFilterSettings(scope = MapFilterScope.MY).toSearchFilter())))
    }

    @Test
    fun `빈 결과를 보관하고 접근해도 3분 만료는 연장하지 않는다`() {
        var now = 0L
        val cache = EmotionRegionMapCache(nowMillis = { now })
        cache.put(key(), emptyList())
        now = 179_999L
        assertEquals(emptyList(), cache.snapshot(key()))
        now = 180_000L
        assertNull(cache.snapshot(key()))
    }

    @Test
    fun `최근 사용한 4개 영역만 유지한다`() {
        val cache = EmotionRegionMapCache()
        val filters =
            (0..4).map {
                MapFilterSettings(
                    period = MapFilterPeriod.CUSTOM,
                    customStartDate = SearchDate(2026, 1, it + 1),
                    customEndDate = SearchDate(2026, 1, it + 2),
                ).toSearchFilter()
            }
        filters.take(4).forEach { cache.put(key(filter = it), regions) }
        cache.snapshot(key(filter = filters[0]))
        cache.put(key(filter = filters[4]), regions)
        assertNull(cache.snapshot(key(filter = filters[1])))
        assertEquals(regions, cache.snapshot(key(filter = filters[0])))
    }

    @Test
    fun `다른 계층의 조회가 기존 계층 캐시를 밀어내지 않는다`() {
        val cache = EmotionRegionMapCache()
        cache.put(key(), regions)
        (0..5).forEach { offset ->
            cache.put(
                key(
                    level = EmotionRegionLevel.SIGUNGU,
                    filter =
                        MapFilterSettings(
                            period = MapFilterPeriod.CUSTOM,
                            customStartDate = SearchDate(2026, 2, offset + 1),
                            customEndDate = SearchDate(2026, 2, offset + 2),
                        ).toSearchFilter(),
                ),
                regions,
            )
        }
        assertEquals(regions, cache.snapshot(key()))
    }

    @Test
    fun `전체 새로고침은 모든 계층의 영역들을 무효화한다`() {
        val cache = EmotionRegionMapCache()
        cache.put(key(), regions)
        cache.put(key(level = EmotionRegionLevel.SIGUNGU), regions)
        cache.invalidateAll()
        assertNull(cache.snapshot(key()))
        assertNull(cache.snapshot(key(level = EmotionRegionLevel.SIGUNGU)))
    }

    @Test
    fun `전체 무효화 이후 늦게 도착한 이전 응답은 캐시를 채우지 못한다`() {
        val cache = EmotionRegionMapCache()
        val firstKey = key()
        val otherKey = key(filter = MapFilterSettings(scope = MapFilterScope.MY).toSearchFilter())
        val staleGeneration = cache.generation()

        cache.invalidateAll()
        val otherGeneration = cache.generation()
        cache.put(firstKey, regions, staleGeneration)
        cache.put(otherKey, regions, otherGeneration)

        assertNull(cache.snapshot(firstKey))
        assertEquals(regions, cache.snapshot(otherKey))
    }
}
