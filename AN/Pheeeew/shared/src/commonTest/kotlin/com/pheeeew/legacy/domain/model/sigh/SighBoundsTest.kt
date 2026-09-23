@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.domain.model.sigh

import kotlin.test.Test
import kotlin.test.assertEquals

class SighBoundsTest {
    @Test
    fun `일반 영역은 그대로 전달된다`() {
        val bounds = SighBounds.fromViewport(west = 127.0, south = 37.0, east = 128.0, north = 38.0)

        assertEquals(127.0, bounds.minLongitude)
        assertEquals(128.0, bounds.maxLongitude)
    }

    @Test
    fun `날짜변경선을 넘는 영역은 180도를 기준으로 나뉘어 전달된다`() {
        val bounds = SighBounds.fromViewport(west = 170.0, south = -10.0, east = 190.0, north = 10.0)

        assertEquals(170.0, bounds.minLongitude)
        assertEquals(-170.0, bounds.maxLongitude)
    }

    @Test
    fun `반대쪽 월드 복제 영역도 -180에서 180 범위로 정규화된다`() {
        val bounds = SighBounds.fromViewport(west = -190.0, south = -10.0, east = -170.0, north = 10.0)

        assertEquals(170.0, bounds.minLongitude)
        assertEquals(-170.0, bounds.maxLongitude)
    }

    @Test
    fun `화면 범위가 360도 이상이면 전체 경계가 전달된다`() {
        val bounds = SighBounds.fromViewport(west = -200.0, south = -10.0, east = 200.0, north = 10.0)

        assertEquals(-180.0, bounds.minLongitude)
        assertEquals(180.0, bounds.maxLongitude)
    }

    @Test
    fun `정규화 후에는 minLongitude가 maxLongitude보다 커도 재정렬하지 않는다`() {
        val bounds = SighBounds.fromViewport(west = 170.0, south = -10.0, east = 190.0, north = 10.0)

        assertEquals(true, bounds.minLongitude > bounds.maxLongitude)
    }
}
