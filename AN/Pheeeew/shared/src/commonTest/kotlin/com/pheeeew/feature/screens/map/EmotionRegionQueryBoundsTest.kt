package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.coversRegionViewport
import com.pheeeew.domain.model.emotion.expandedForRegionQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmotionRegionQueryBoundsTest {
    @Test
    fun `작은 이동은 같은 격자 조회 범위에 들어간다`() {
        val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
        val moved = EmotionMapBounds(126.91, 37.5, 127.11, 37.6)
        val query = area.expandedForRegionQuery(EmotionRegionLevel.SIGUNGU)
        assertEquals(EmotionMapBounds(126.75, 37.375, 127.25, 37.625), query)
        assertEquals(query, moved.expandedForRegionQuery(EmotionRegionLevel.SIGUNGU))
        assertTrue(query.coversRegionViewport(area))
    }

    @Test
    fun `극지방과 날짜 변경선에서도 유효한 조회 범위를 만든다`() {
        val areas = listOf(
            EmotionMapBounds(179.0, -10.0, -179.0, 10.0),
            EmotionMapBounds(-180.0, 89.8, -179.9, 90.0),
            EmotionMapBounds(179.9, -90.0, 180.0, -89.8),
            EmotionMapBounds(-170.0, -80.0, 170.0, 80.0),
        )
        EmotionRegionLevel.entries.forEach { level ->
            areas.forEach { area ->
                val query = area.expandedForRegionQuery(level)
                assertTrue(query.isValid())
                assertTrue(query.coversRegionViewport(area))
            }
        }
    }
}
