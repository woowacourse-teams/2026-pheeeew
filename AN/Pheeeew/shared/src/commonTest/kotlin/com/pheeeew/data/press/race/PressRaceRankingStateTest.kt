package com.pheeeew.data.press.race

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressRaceRankingStateTest {
    @Test
    fun viewChangeAfterMidnightKeepsNewDateEvenWhenReadyIsFromYesterday() {
        val snapshot = snapshot(PressRaceView.ALL).copy(date = "2026-10-28")
        val session = ready(snapshot).copy(ready = ready(snapshot).ready!!.copy(date = "2026-10-27"))
        val state =
            PressRaceRankingState()
                .update(session)
                .update(session.copy(view = PressRaceView.PRESS, isAwaitingSnapshot = true))
        assertEquals("2026-10-28", state.all?.date)
        assertFalse(state.isFresh(PressRaceView.ALL))
    }

    @Test
    fun pressEmptyTop100DoesNotEraseAllAndCachedViewIsNotFresh() {
        val all = snapshot(PressRaceView.ALL)
        val press = snapshot(PressRaceView.PRESS).copy(top100 = emptyList())
        val state = PressRaceRankingState().update(ready(all)).update(ready(press))
        assertEquals(all, state.all)
        assertEquals(press, state.press)
        assertFalse(state.isFresh(PressRaceView.ALL))
        assertTrue(state.isFresh(PressRaceView.PRESS))
    }

    @Test
    fun failureAndReadyWithoutBaselineRetainOnlyStaleCache() {
        val snapshot = snapshot(PressRaceView.ALL)
        val loaded = PressRaceRankingState().update(ready(snapshot))
        val failed = loaded.update(PressRaceSessionState(phase = PressRacePhase.Failed, snapshot = snapshot))
        assertEquals(snapshot, failed.all)
        assertFalse(failed.isFresh(PressRaceView.ALL))
        val awaiting = failed.update(ready(snapshot).copy(isAwaitingSnapshot = true))
        assertFalse(awaiting.isFresh(PressRaceView.ALL))
    }

    @Test
    fun newDateReadyInvalidatesYesterdayAcrossBothViews() {
        val snapshot = snapshot(PressRaceView.ALL)
        val loaded = PressRaceRankingState().update(ready(snapshot))
        val next =
            loaded.update(
                ready(
                    snapshot,
                ).copy(ready = ready(snapshot).ready!!.copy(date = "2026-10-28"), isAwaitingSnapshot = true),
            )
        assertNull(next.all)
        assertNull(next.press)
    }

    private fun ready(snapshot: PressRaceSnapshot) =
        PressRaceSessionState(
            phase = PressRacePhase.Ready,
            view = snapshot.view,
            ready = PressRaceReady("server", "connection", snapshot.date, emptyMap(), emptyList()),
            snapshot = snapshot,
        )

    private fun snapshot(view: PressRaceView): PressRaceSnapshot {
        val me = PressRaceParticipant("me", "same", 10, 101, true)
        return PressRaceSnapshot(
            "server",
            "connection",
            "2026-10-27",
            "1",
            "2026-10-27T00:00:00Z",
            view,
            0,
            me,
            listOf(me),
            listOf(me),
            true,
            PressRaceOvertakes(0, 0),
        )
    }
}
