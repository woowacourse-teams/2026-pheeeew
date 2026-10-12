package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmotionPinQueryBoundsTest {
    @Test
    fun `화면 너비와 높이의 25퍼센트를 각 경계에 더한다`() {
        val result = EmotionMapBounds(20.0, 10.0, 28.0, 14.0).expandedForPinQuery()
        assertEquals(18.0, result.minLongitude, 0.000001)
        assertEquals(30.0, result.maxLongitude, 0.000001)
        assertEquals(9.0, result.minLatitude, 0.000001)
        assertEquals(15.0, result.maxLatitude, 0.000001)
    }

    @Test
    fun `날짜 변경선을 넘는 화면도 양쪽으로 넓힌다`() {
        val result = EmotionMapBounds(179.0, 37.0, -179.0, 38.0).expandedForPinQuery()
        assertEquals(178.5, result.minLongitude, 0.000001)
        assertEquals(-178.5, result.maxLongitude, 0.000001)
        assertTrue(result.isValid())
    }

    @Test
    fun `확장 중 날짜 변경선을 넘으면 경도를 감싼다`() {
        val result = EmotionMapBounds(178.0, 37.0, 179.8, 38.0).expandedForPinQuery()
        assertEquals(177.55, result.minLongitude, 0.000001)
        assertEquals(-179.75, result.maxLongitude, 0.000001)
        assertTrue(result.isValid())
    }

    @Test
    fun `극지방과 전세계 범위는 유효한 좌표 범위로 제한한다`() {
        val result = EmotionMapBounds(-170.0, -90.0, 170.0, 90.0).expandedForPinQuery()
        assertEquals(EmotionMapBounds(-180.0, -90.0, 180.0, 90.0), result)
        assertTrue(result.isValid())
    }
}
