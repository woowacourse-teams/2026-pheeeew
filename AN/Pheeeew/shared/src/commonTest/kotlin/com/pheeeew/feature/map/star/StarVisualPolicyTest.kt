@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.map.star

import kotlin.test.Test
import kotlin.test.assertEquals

class StarVisualPolicyTest {
    @Test
    fun `단계별 시각 토큰은 서로 다른 안정적인 image key를 가진다`() {
        val visuals = StarAgeStage.entries.map(StarVisualPolicy::visualFor)

        assertEquals(visuals.size, visuals.map(StarVisual::imageKey).toSet().size)
        assertEquals(StarVisualPolicy.allVisuals, visuals)
    }

    @Test
    fun `같은 별 ID는 지도 재조회 후에도 같은 팔레트를 사용한다`() {
        val first = StarVisualPolicy.visualFor("sigh-42")
        val second = StarVisualPolicy.visualFor("sigh-42")

        assertEquals(first, second)
    }

    @Test
    fun `별 ID가 달라지면 생애 가이드 팔레트 중 하나를 사용한다`() {
        val palette = StarVisualPolicy.allVisuals.map(StarVisual::imageKey).toSet() - StarVisualPolicy.UNKNOWN_IMAGE_KEY

        assertEquals(true, StarVisualPolicy.visualFor("sigh-1").imageKey in palette)
        assertEquals(true, StarVisualPolicy.visualFor("sigh-2").imageKey in palette)
    }

    @Test
    fun `Unknown은 낮은 opacity의 fallback 시각을 사용한다`() {
        val visual = StarVisualPolicy.visualFor(StarAgeStage.Unknown)

        assertEquals(StarVisualPolicy.UNKNOWN_IMAGE_KEY, visual.imageKey)
        assertEquals(0.85f, visual.opacity)
    }
}
