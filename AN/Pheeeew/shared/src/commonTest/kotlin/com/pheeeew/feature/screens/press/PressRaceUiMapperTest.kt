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
    fun participantKeySurvivesCrossingsAndNicknameChangesButNotDateChanges() {
        val before = PressNearbyRankRow(10, "same", 10, true, "me")
        val after = before.copy(rank = 9, nickname = "renamed", count = 13)
        assertEquals(
            nearbyRankingItemKey("2026-10-27", before, 1),
            nearbyRankingItemKey("2026-10-27", after, 0),
        )
        kotlin.test.assertNotEquals(
            nearbyRankingItemKey("2026-10-27", before, 1),
            nearbyRankingItemKey("2026-10-28", before, 1),
        )
        kotlin.test.assertNotEquals(
            nearbyRankingItemKey("2026-10-27", before, 1),
            nearbyRankingItemKey("2026-10-27", before.copy(publicId = "other"), 1),
        )
    }

    @Test
    fun duplicateNicknamesUsePublicIdAndZeroActivityRemainsKnown() {
        val me = PressRaceParticipant("me", "same", 10, 2, true)
        val other = me.copy(publicId = "other")
        val state = state(me, listOf(other, me))
        assertEquals(listOf(false, true), state.toNearbyRanking().rows.map { it.isMe })
        assertEquals(listOf("other", "me"), state.toNearbyRanking().rows.map { it.publicId })
        assertEquals("2026-10-27", state.toNearbyRanking().date)
        assertNull(state.toNearbyRanking().progress)
        assertEquals(PressActivityStatus.Known, state.toActivity().status)
        assertEquals(0L, state.toActivity().count)
        val failed = state.copy(session = state.session.copy(phase = PressRacePhase.Failed))
        assertEquals(2, failed.toNearbyRanking().rows.size)
        assertNull(failed.toActivity().count)
        assertEquals(PressActivityStatus.Unknown, failed.toActivity().status)
    }

    @Test
    fun returningScreenKeepsActivityAndRowsUntilFreshBaselineArrives() {
        val me = PressRaceParticipant("me", "same", 10, 2, true)
        val initial = state(me, listOf(me))
        val snapshot = initial.press!!.copy(activeCount = 4)
        val loaded = initial.update(initial.session.copy(snapshot = snapshot))
        for (phase in listOf(
            PressRacePhase.Disconnected,
            PressRacePhase.Connecting,
            PressRacePhase.Initializing,
            PressRacePhase.Ready,
        )) {
            val awaiting = loaded.copy(session = loaded.session.copy(phase = phase, isAwaitingSnapshot = true))
            assertEquals(4L, awaiting.toActivity().count)
            assertEquals(PressActivityStatus.Cached, awaiting.toActivity().status)
            assertEquals(3, awaiting.toActivity().avatarCount)
            assertEquals(1L, awaiting.toActivity().avatarOverflowCount)
            assertEquals(PressNearbyRankingStatus.Cached, awaiting.toNearbyRanking().status)
            assertEquals(loaded.toNearbyRanking().rows, awaiting.toNearbyRanking().rows)
        }
        val fresh = loaded.update(loaded.session.copy(snapshot = snapshot.copy(activeCount = 1, snapshotVersion = "2")))
        assertEquals(PressActivityStatus.Known, fresh.toActivity().status)
        assertEquals(1L, fresh.toActivity().count)
        val failed = loaded.copy(session = loaded.session.copy(phase = PressRacePhase.Failed))
        assertNull(failed.toActivity().count)
        assertEquals(PressActivityStatus.Unknown, failed.toActivity().status)
        assertEquals(PressNearbyRankingStatus.Reconnecting, failed.toNearbyRanking().status)
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
