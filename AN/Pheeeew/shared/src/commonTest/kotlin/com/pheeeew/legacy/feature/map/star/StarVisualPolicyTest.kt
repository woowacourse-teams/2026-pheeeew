@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.feature.map.star

import com.pheeeew.legacy.core.designsystem.DesignSystemColors
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
    fun `두 번째 생애 단계는 기존 별 색상을 사용한다`() {
        assertEquals(
            DesignSystemColors.STAR_WARM_HEX,
            StarVisualPolicy.visualFor(StarAgeStage.Warm).colorHex,
        )
    }

    @Test
    fun `Unknown은 낮은 opacity의 fallback 시각을 사용한다`() {
        val visual = StarVisualPolicy.visualFor(StarAgeStage.Unknown)

        assertEquals(StarVisualPolicy.UNKNOWN_IMAGE_KEY, visual.imageKey)
        assertEquals(0.85f, visual.opacity)
    }
}
