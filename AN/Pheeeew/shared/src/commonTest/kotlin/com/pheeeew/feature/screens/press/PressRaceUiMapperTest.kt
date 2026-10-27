package com.pheeeew.feature.screens.press

import com.pheeeew.data.press.race.PressRaceOvertakes
import com.pheeeew.data.press.race.PressRaceParticipant
import com.pheeeew.data.press.race.PressRacePhase
import com.pheeeew.data.press.race.PressRaceRankingState
import com.pheeeew.data.press.race.PressRaceReady
import com.pheeeew.data.press.race.PressRaceSessionState
import com.pheeeew.data.press.race.PressRaceSnapshot
import com.pheeeew.data.press.race.PressRaceView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressRaceUiMapperTest {
    @Test
    fun duplicateNicknamesUsePublicIdAndZeroActivityRemainsKnown() {
        val me = PressRaceParticipant("me", "same", 10, 2, true)
        val other = me.copy(publicId = "other")
        val state = state(me, listOf(other, me))
        assertEquals(listOf(false, true), state.toNearbyRanking().rows.map { it.isMe })
        assertNull(state.toNearbyRanking().progress)
        assertEquals(PressActivityStatus.Known, state.toActivity().status)
        assertEquals(0L, state.toActivity().count)
        val failed = state.copy(session = state.session.copy(phase = PressRacePhase.Failed))
        assertEquals(2, failed.toNearbyRanking().rows.size)
        assertNull(failed.toActivity().count)
        assertEquals(PressActivityStatus.Unknown, failed.toActivity().status)
    }

    @Test
    fun nonparticipantDoesNotAcquireFakeRankOrIdentity() {
        val me = PressRaceParticipant(null, null, 0, null, false)
        val state = state(me, emptyList())
        assertTrue(state.toNearbyRanking().participationPending)
        assertTrue(state.toNearbyRanking().rows.isEmpty())
    }

    private fun state(
        me: PressRaceParticipant,
        nearby: List<PressRaceParticipant>,
    ): PressRaceRankingState {
        val snapshot =
            PressRaceSnapshot(
                "s",
                "c",
                "2026-10-27",
                "1",
                "2026-10-27T00:00:00Z",
                PressRaceView.PRESS,
                0,
                me,
                nearby,
                emptyList(),
                true,
                PressRaceOvertakes(0, 0),
            )
        return PressRaceRankingState().update(
            PressRaceSessionState(
                PressRacePhase.Ready,
                PressRaceView.PRESS,
                PressRaceReady("s", "c", snapshot.date, emptyMap(), emptyList()),
                snapshot,
            ),
        )
    }
}
