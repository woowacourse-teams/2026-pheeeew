@file:Suppress("NonAsciiCharacters")

package com.pheeeew.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreathStrengthScorerTest {
    @Test
    fun `음량만으로는 활성화 기준에 도달하지 않는다`() {
        val score = BreathStrengthScorer.score(1f, 0f, 0f, 0f)
        assertTrue(score < 0.18f)
    }

    @Test
    fun `저주파와 질감은 음량에 가중된다`() {
        val plain = BreathStrengthScorer.score(0.5f, 0f, 0f, 0f)
        val textured = BreathStrengthScorer.score(0.5f, 1f, 1f, 0f)
        assertTrue(textured > plain)
    }

    @Test
    fun `말소리 대역이 강하면 점수를 감점한다`() {
        val breathLike = BreathStrengthScorer.score(0.8f, 0.8f, 0.8f, speechBandPresence = 0f, previousSmoothedStrength = 0f)
        val speechLike = BreathStrengthScorer.score(0.8f, 0.8f, 0.8f, speechBandPresence = 1f, previousSmoothedStrength = 0f)
        assertTrue(speechLike < breathLike)
    }

    @Test
    fun `저주파가 강하고 질감이 낮은 유성음은 활성화 기준 아래로 감점한다`() {
        val vowelScore = BreathStrengthScorer.score(1f, 0.8f, 0f, speechBandPresence = 1f, previousSmoothedStrength = 0f)
        assertTrue(vowelScore < 0.18f)
    }

    @Test
    fun `무음은 이전 강도도 즉시 초기화한다`() {
        assertEquals(0f, BreathStrengthScorer.score(0.01f, 1f, 1f, 0.8f))
    }
}
