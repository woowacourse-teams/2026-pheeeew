package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapZoomPolicyTest {
    @Test
    fun `줌 경계에서 시도 시군구 개별 핀으로 전환한다`() {
        assertEquals(EmotionRegionLevel.SIDO, MapZoomPolicy.regionLevelForZoom(8.99))
        assertEquals(EmotionRegionLevel.SIGUNGU, MapZoomPolicy.regionLevelForZoom(9.0))
        assertEquals(EmotionRegionLevel.SIGUNGU, MapZoomPolicy.regionLevelForZoom(11.99))
        assertNull(MapZoomPolicy.regionLevelForZoom(12.0))
        assertNull(MapZoomPolicy.regionLevelForZoom(13.99))
        assertNull(MapZoomPolicy.regionLevelForZoom(14.0))
    }

    @Test
    fun `각 클러스터 클릭은 다음 조회 계층의 경계 줌으로 이동한다`() {
        assertEquals(9.0, MapZoomPolicy.focusZoomForRegion(EmotionRegionLevel.SIDO))
        assertEquals(12.0, MapZoomPolicy.focusZoomForRegion(EmotionRegionLevel.SIGUNGU))
        assertEquals(
            EmotionRegionLevel.SIGUNGU,
            MapZoomPolicy.regionLevelForZoom(MapZoomPolicy.focusZoomForRegion(EmotionRegionLevel.SIDO)),
        )
        assertNull(
            MapZoomPolicy.regionLevelForZoom(MapZoomPolicy.focusZoomForRegion(EmotionRegionLevel.SIGUNGU)),
        )
    }
}
