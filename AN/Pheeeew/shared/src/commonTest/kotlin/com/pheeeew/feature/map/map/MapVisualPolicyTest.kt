@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.map.map

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MapVisualPolicyTest {
    @Test
    fun `지도 정보는 초기 화면부터 상세 화면까지 줌 단계에 따라 점진적으로 노출된다`() {
        assertTrue(MapDarkStyle.INITIAL_ZOOM < MapDarkStyle.IMPORTANT_POI_MIN_ZOOM)
        assertTrue(MapDarkStyle.IMPORTANT_POI_MIN_ZOOM < MapDarkStyle.BUILDING_LABEL_MIN_ZOOM)
        assertTrue(MapDarkStyle.BUILDING_LABEL_MIN_ZOOM < MapDarkStyle.GENERAL_DETAIL_MIN_ZOOM)
    }

    @Test
    fun `지도 요소는 서로 다른 명도 토큰을 사용한다`() {
        assertNotEquals(MapDarkStyle.MAP_BACKGROUND_HEX, MapDarkStyle.LAND_HEX)
        assertNotEquals(MapDarkStyle.LAND_HEX, MapDarkStyle.BUILDING_HEX)
        assertNotEquals(MapDarkStyle.ROAD_HEX, MapDarkStyle.MAJOR_ROAD_HEX)
        assertNotEquals(MapDarkStyle.LABEL_HEX, MapDarkStyle.LABEL_HALO_HEX)
    }
}
