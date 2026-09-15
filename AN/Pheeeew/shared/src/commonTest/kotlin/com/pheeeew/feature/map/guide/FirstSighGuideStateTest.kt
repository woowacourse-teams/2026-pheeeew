package com.pheeeew.feature.map.guide

import com.pheeeew.feature.map.overlay.SighPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class FirstSighGuideStateTest {
    @Test
    fun `실제 한숨 단계가 가이드 단계에 대응한다`() {
        assertEquals(FirstSighGuideStep.TapButton, SighPhase.Idle.toFirstSighGuideStep())
        assertEquals(FirstSighGuideStep.Blow, SighPhase.Listening.toFirstSighGuideStep())
        assertEquals(FirstSighGuideStep.Blow, SighPhase.NeedsMore.toFirstSighGuideStep())
        assertEquals(FirstSighGuideStep.SwipeUp, SighPhase.Quiet.toFirstSighGuideStep())
        assertEquals(FirstSighGuideStep.Hidden, SighPhase.Bursting.toFirstSighGuideStep())
    }

    @Test
    fun `신규 설치는 저장값이 없으면 가이드를 완료하지 않은 것으로 본다`() {
        assertEquals(
            false,
            resolveFirstSighGuideCompleted(
                hasCompletedOnboarding = false,
                storedGuideCompletion = null,
            ),
        )
    }

    @Test
    fun `기존 설치는 저장값이 없으면 가이드를 완료한 것으로 마이그레이션한다`() {
        assertEquals(
            true,
            resolveFirstSighGuideCompleted(
                hasCompletedOnboarding = true,
                storedGuideCompletion = null,
            ),
        )
    }

    @Test
    fun `명시적으로 저장된 가이드 상태를 우선한다`() {
        assertEquals(true, resolveFirstSighGuideCompleted(false, true))
        assertEquals(false, resolveFirstSighGuideCompleted(true, false))
    }
}
