package com.pheeeew.feature.map.guide

import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.feature.map.PendingSighDraft
import com.pheeeew.feature.map.SighReleaseState
import com.pheeeew.feature.map.overlay.SighPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class FirstSighGuideStateTest {
    @Test
    fun `실제 한숨 단계가 가이드 단계에 대응한다`() {
        val coordinate = Coordinate(latitude = 37.55, longitude = 126.95)
        val draft = PendingSighDraft(requestId = "request-id", coordinate = coordinate)
        val command = CreateSighCommand(requestId = "request-id", coordinate = coordinate, memo = "메모")

        assertEquals(
            FirstSighGuideStep.TapButton,
            firstSighGuideStepFor(SighReleaseState.Idle, SighPhase.Idle),
        )
        assertEquals(
            FirstSighGuideStep.Memo,
            firstSighGuideStepFor(SighReleaseState.EditingMemo(draft), SighPhase.Idle),
        )
        assertEquals(
            FirstSighGuideStep.Blow,
            firstSighGuideStepFor(SighReleaseState.AwaitingBreath(command), SighPhase.Listening),
        )
        assertEquals(
            FirstSighGuideStep.Blow,
            firstSighGuideStepFor(SighReleaseState.AwaitingBreath(command), SighPhase.NeedsMore),
        )
        assertEquals(
            FirstSighGuideStep.SwipeUp,
            firstSighGuideStepFor(SighReleaseState.AwaitingBreath(command), SighPhase.Quiet),
        )
        assertEquals(
            FirstSighGuideStep.Blow,
            firstSighGuideStepFor(
                SighReleaseState.AwaitingBreath(command),
                SighPhase.Quiet,
                isSwipeUpPromptReady = false,
            ),
        )
        assertEquals(
            FirstSighGuideStep.Hidden,
            firstSighGuideStepFor(SighReleaseState.AwaitingBreath(command), SighPhase.Bursting),
        )
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
