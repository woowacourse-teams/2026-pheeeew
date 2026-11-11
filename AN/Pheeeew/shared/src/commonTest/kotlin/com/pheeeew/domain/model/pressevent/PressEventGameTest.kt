package com.pheeeew.domain.model.pressevent

import com.pheeeew.domain.model.emotion.EmotionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressEventGameTest {
    private var now = 0L
    private val game = PressEventGame { now }

    @Test
    fun acceptsAllFiveButtonsOnlyDuringTheTwentySecondWindow() {
        assertFalse(game.press(EmotionState.ANGRY))
        game.start()
        assertEquals(3, game.state.seconds)
        now = 999
        game.tick()
        assertEquals(3, game.state.seconds)
        now = 1_000
        game.tick()
        assertEquals(2, game.state.seconds)
        now = 2_999
        assertFalse(game.press(EmotionState.ANGRY))
        now = 3_000
        EmotionState.entries.forEach { assertTrue(game.press(it)) }
        assertEquals(5L, game.state.total)
        assertEquals(20, game.state.seconds)
        now = 22_999
        assertTrue(game.press(EmotionState.ANGRY))
        now = 23_000
        assertFalse(game.press(EmotionState.ANGRY))
        assertEquals(PressEventGamePhase.Finished, game.state.phase)
        assertEquals(6L, game.state.total)
        assertEquals(0, game.state.seconds)
    }

    @Test
    fun delayedTimerDoesNotExtendTheGameOrAcceptLateInput() {
        game.start()
        now = 25_000
        assertFalse(game.press(EmotionState.ANGRY))
        assertEquals(PressEventGamePhase.Finished, game.state.phase)
        assertEquals(0L, game.state.total)
    }

    @Test
    fun duplicateStartDoesNotRestartCountdownAndReplayClearsScores() {
        game.start()
        now = 2_000
        game.start()
        now = 3_000
        assertTrue(game.press(EmotionState.EXHAUSTED))
        game.start()
        now = 23_000
        game.tick()
        game.start()
        assertEquals(PressEventGamePhase.Countdown, game.state.phase)
        assertEquals(0L, game.state.total)
        now = 25_999
        assertFalse(game.press(EmotionState.EXHAUSTED))
        now = 26_000
        assertTrue(game.press(EmotionState.EXHAUSTED))
    }

    @Test
    fun leavingCancelsCountdownAndPlayingButPreservesFinishedResults() {
        game.start()
        game.cancelActiveAttempt()
        assertEquals(PressEventGameSnapshot(), game.state)
        game.start()
        now = 3_000
        game.press(EmotionState.FRUSTRATED)
        game.cancelActiveAttempt()
        assertEquals(PressEventGameSnapshot(), game.state)
        assertFalse(game.press(EmotionState.FRUSTRATED))
        game.start()
        now = 6_000
        game.press(EmotionState.FRUSTRATED)
        now = 26_000
        game.tick()
        game.cancelActiveAttempt()
        assertEquals(PressEventGamePhase.Finished, game.state.phase)
        assertEquals(1L, game.state.total)
    }

    @Test
    fun returningFromResultOrPlayingAlwaysShowsRankingAndReplayStartsAtTwentySeconds() {
        game.start()
        now = 3_000
        game.press(EmotionState.EXHAUSTED)
        now = 3_670
        game.tick()
        assertEquals(19_330L, game.remainingMillis)
        game.reset()
        assertEquals(PressEventGameSnapshot(), game.state)
        assertEquals(20_000L, game.remainingMillis)
        game.start()
        assertEquals(20_000L, game.remainingMillis)
        now = 26_670
        game.tick()
        assertEquals(PressEventGamePhase.Finished, game.state.phase)
        game.reset()
        assertEquals(PressEventGamePhase.Ready, game.state.phase)
    }

    @Test
    fun acceptsInputBeyondFourHundredUntilTheTwentySecondWindowEnds() {
        game.start()
        now = 3_000
        repeat(400) { assertTrue(game.press(EmotionState.ANGRY)) }
        assertTrue(game.press(EmotionState.EXHAUSTED))
        assertEquals(401L, game.state.total)
        assertEquals(PressEventGamePhase.Playing, game.state.phase)
        now = 23_000
        game.tick()
        assertEquals(PressEventGamePhase.Finished, game.state.phase)
    }
}
