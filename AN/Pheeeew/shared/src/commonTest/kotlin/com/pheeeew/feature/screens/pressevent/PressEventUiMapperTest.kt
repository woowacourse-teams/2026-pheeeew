package com.pheeeew.feature.screens.pressevent

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.pressevent.PressEventGamePhase
import com.pheeeew.domain.model.pressevent.PressEventGameSnapshot
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PressEventUiMapperTest {
    @Test
    fun formatsTimerAsSecondsAndHundredths() {
        assertEquals("20:00", eventTimerText(20_000))
        assertEquals("19:33", eventTimerText(19_330))
        assertEquals("00:01", eventTimerText(1))
        assertEquals("00:00", eventTimerText(0))
        assertEquals("00:00", eventTimerText(-1))
    }

    @Test
    fun convertsDomainSnapshotIntoUiPhaseAndEmotionCounts() {
        val state =
            PressEventGameSnapshot(
                phase = PressEventGamePhase.Playing,
                seconds = 12,
                counts = mapOf(EmotionState.ANGRY to 3, EmotionState.FRUSTRATED to 2),
            ).toUiState()
        assertEquals(PressEventPhase.Playing, state.phase)
        assertEquals(12, state.seconds)
        assertEquals(mapOf(EmotionKind.Angry to 3L, EmotionKind.Blocked to 2L), state.counts)
        assertEquals(5L, state.total)
        assertEquals(PressEventPhase.Ready, PressEventGameSnapshot().toUiState().phase)
        assertEquals(PressEventPhase.Countdown, PressEventGameSnapshot(PressEventGamePhase.Countdown).toUiState().phase)
        assertEquals(PressEventPhase.Finished, PressEventGameSnapshot(PressEventGamePhase.Finished).toUiState().phase)
    }
}
