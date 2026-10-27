package com.pheeeew.feature.screens.ranking

import androidx.lifecycle.ViewModelStore
import com.pheeeew.data.press.race.FakePressRaceRankings
import com.pheeeew.data.press.race.PressRaceOvertakes
import com.pheeeew.data.press.race.PressRaceParticipant
import com.pheeeew.data.press.race.PressRacePhase
import com.pheeeew.data.press.race.PressRaceRankingState
import com.pheeeew.data.press.race.PressRaceReady
import com.pheeeew.data.press.race.PressRaceSessionState
import com.pheeeew.data.press.race.PressRaceSnapshot
import com.pheeeew.data.press.race.PressRaceView
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import com.pheeeew.feature.screens.ranking.press.toRaceRows
import com.pheeeew.feature.screens.ranking.press.toRankingUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressRankingViewModelTest {
    @Test
    fun snapshotStreamUpdatesWithoutPollingAndRefreshBorrowsOwner() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val source = FakePressRaceRankings()
                val vm = PressRankingViewModel(source)
                store.put("ranking", vm)
                runCurrent()
                assertEquals(PressRankingStatus.Loading, vm.uiState.value.status)
                source.state.value = rankingState()
                runCurrent()
                assertEquals(PressRankingStatus.Ready, vm.uiState.value.status)
                assertEquals("2026-10-27", vm.uiState.value.date)
                assertEquals(
                    10L,
                    vm.uiState.value.me
                        ?.count,
                )
                vm.onRefresh()
                vm.onRetry()
                assertEquals(listOf(PressRaceView.ALL), source.refreshed)
                assertEquals(0L, testScheduler.currentTime)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun onlyUserRefreshShowsIndicatorAndFreshBaselineClearsIt() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val source = FakePressRaceRankings()
                val loaded = rankingState()
                source.state.value = loaded.copy(session = loaded.session.copy(isAwaitingSnapshot = true))
                val vm = PressRankingViewModel(source)
                store.put("refresh", vm)
                runCurrent()
                assertFalse(vm.uiState.value.isRefreshing)
                source.state.value = loaded
                runCurrent()
                vm.onRefresh()
                assertTrue(vm.uiState.value.isRefreshing)
                source.state.value = loaded.copy(session = loaded.session.copy(isAwaitingSnapshot = true))
                runCurrent()
                assertTrue(vm.uiState.value.isRefreshing)
                source.state.value = loaded
                runCurrent()
                assertFalse(vm.uiState.value.isRefreshing)
                source.state.value = loaded.copy(session = loaded.session.copy(view = null))
                runCurrent()
                vm.onRefresh()
                assertEquals(1, source.refreshed.size)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun duplicateNamesAndTiedRanksRetainIdentityAndOrder() {
        val state = rankingState()
        val ui = state.toRankingUiState()
        assertEquals(listOf(2L, 2L), ui.people.map { it.rank })
        assertEquals(listOf(false, true), ui.people.toRaceRows().map { it.isMe })
        assertEquals(listOf("other", "me"), ui.people.toRaceRows().map { it.publicId })
    }

    @Test
    fun top100DoesNotLimitMyCardAndLongRankIsPreserved() {
        val state = rankingState()
        val snapshot = state.all!!.copy(me = state.all.me.copy(rank = Long.MAX_VALUE))
        val ui = state.copy(all = snapshot).toRankingUiState()
        assertEquals(Long.MAX_VALUE, ui.me?.rank)
        assertEquals(2, ui.people.size)
    }

    @Test
    fun zeroThroughThreeParticipantsAndNonparticipationNeverInventZeroRank() {
        for (size in 0..3) {
            val state = rankingState()
            val me = PressRaceParticipant(null, null, 0, null, false)
            val member = state.all!!.top100.first()
            val ui =
                state
                    .copy(
                        all =
                            state.all.copy(
                                me = me,
                                top100 = List(size) { member.copy(publicId = "id-$it") },
                            ),
                    ).toRankingUiState()
            assertEquals(size, ui.people.size)
            assertNull(ui.me)
            assertTrue(ui.people.none { it.isMe })
        }
    }

    @Test
    fun staleCacheSurvivesFailureAndReadyAloneDoesNotFinishRefresh() {
        val state = rankingState()
        val failed = state.copy(session = state.session.copy(phase = PressRacePhase.Failed))
        val ui = failed.toRankingUiState()
        assertEquals(PressRankingStatus.Ready, ui.status)
        assertEquals(2, ui.people.size)
        assertTrue(ui.isStale)
        assertTrue(ui.hasRefreshError)
        assertFalse(ui.isRefreshing)
        val refreshing = state.copy(session = state.session.copy(isAwaitingSnapshot = true)).toRankingUiState()
        assertFalse(refreshing.isRefreshing)
        assertTrue(refreshing.isStale)
        val noRank = state.all!!.copy(me = state.all.me.copy(rank = null))
        assertNull(state.copy(all = noRank).toRankingUiState().me)
    }

    private fun rankingState(): PressRaceRankingState {
        val me = PressRaceParticipant("me", "same", 10, 2, true)
        val snapshot =
            PressRaceSnapshot(
                "s",
                "c",
                "2026-10-27",
                "1",
                "2026-10-27T00:00:00Z",
                PressRaceView.ALL,
                0,
                me,
                listOf(me),
                listOf(me.copy(publicId = "other"), me),
                true,
                PressRaceOvertakes(0, 0),
            )
        return PressRaceRankingState().update(
            PressRaceSessionState(
                PressRacePhase.Ready,
                PressRaceView.ALL,
                PressRaceReady("s", "c", snapshot.date, emptyMap(), emptyList()),
                snapshot,
            ),
        )
    }
}
