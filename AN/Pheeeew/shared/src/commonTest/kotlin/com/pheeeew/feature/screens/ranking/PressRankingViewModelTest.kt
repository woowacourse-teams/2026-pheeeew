package com.pheeeew.feature.screens.ranking

import androidx.lifecycle.ViewModelStore
import com.pheeeew.data.press.race.FakePressRaceRankings
import com.pheeeew.data.press.race.PressRaceComparisonState
import com.pheeeew.data.press.race.PressRaceOvertakes
import com.pheeeew.data.press.race.PressRaceParticipant
import com.pheeeew.data.press.race.PressRacePhase
import com.pheeeew.data.press.race.PressRaceRankingState
import com.pheeeew.data.press.race.PressRaceReady
import com.pheeeew.data.press.race.PressRaceSessionState
import com.pheeeew.data.press.race.PressRaceSnapshot
import com.pheeeew.data.press.race.PressRaceView
import com.pheeeew.data.press.race.toRankingSnapshot
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import com.pheeeew.feature.screens.ranking.press.components.runningRaceResource
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
    fun everyApiEmotionSelectsItsRunnerWithoutChangingParticipantData() {
        val base = rankingState()
        val snapshot = requireNotNull(base.all)
        val cases =
            mapOf(
                "ANGRY" to EmotionKind.Angry,
                "DISCOURAGED" to EmotionKind.Defeated,
                "EXHAUSTED" to EmotionKind.Tired,
                "FRUSTRATED" to EmotionKind.Blocked,
                "IRRITATED" to EmotionKind.Annoyed,
            )
        val resources = mutableSetOf<Any>()
        for ((raw, expected) in cases) {
            val changed = snapshot.copy(top100 = snapshot.top100.map { it.copy(representativeState = raw) })
            val rows =
                base
                    .copy(all = changed)
                    .toRankingUiState()
                    .people
                    .toRaceRows()
            assertTrue(rows.isNotEmpty())
            assertTrue(rows.all { it.emotion == expected })
            assertEquals(snapshot.top100.map { it.publicId }, rows.map { it.publicId })
            assertEquals(snapshot.top100.map { it.rank }, rows.map { it.rank })
            for (second in listOf(false, true)) {
                val resource = requireNotNull(runningRaceResource(rows.first().emotion, second))
                assertTrue(resources.add(resource), "Each emotion and pose needs its own asset")
            }
        }
        assertEquals(10, resources.size)
        assertNull(runningRaceResource(null, false))
        assertNull(runningRaceResource(null, true))
    }

    @Test
    fun wsRepresentativeEmotionUpdatesWithoutChangingRankOrIdentityAndSurvivesCache() {
        val base = rankingState()
        val snapshot = base.all!!
        val first =
            snapshot.copy(
                me = snapshot.me.copy(rank = 101, representativeState = "ANGRY"),
                top100 =
                    snapshot.top100
                        .filter { it.publicId != snapshot.me.publicId }
                        .map { it.copy(representativeState = "EXHAUSTED") },
            )
        val state = base.update(base.session.copy(snapshot = first))
        val ui = state.toRankingUiState()
        assertEquals(EmotionKind.Angry, ui.me?.emotion)
        assertEquals(101L, ui.me?.rank)
        assertTrue(ui.people.all { it.emotion == EmotionKind.Tired })
        val changed =
            first.copy(
                snapshotVersion = "2",
                top100 = first.top100.map { it.copy(representativeState = "IRRITATED") },
            )
        val next = state.update(state.session.copy(snapshot = changed)).toRankingUiState()
        assertEquals(ui.people.map { it.publicId }, next.people.map { it.publicId })
        assertEquals(ui.people.map { it.rank }, next.people.map { it.rank })
        assertEquals(ui.people.map { it.count }, next.people.map { it.count })
        assertTrue(next.people.toRaceRows().all { it.emotion == EmotionKind.Annoyed })
        val stale = state.copy(session = state.session.copy(phase = PressRacePhase.Failed)).toRankingUiState()
        assertTrue(stale.isStale)
        assertEquals(ui.people, stale.people)
        for (raw in listOf(null, "UNKNOWN_STATE")) {
            val unsupported = first.copy(top100 = first.top100.map { it.copy(representativeState = raw) })
            assertTrue(
                state
                    .copy(all = unsupported)
                    .toRankingUiState()
                    .people
                    .all { it.emotion == null },
            )
        }
    }

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
    fun cachedComparisonAllShowsRefreshIndicatorUntilNewReadySnapshotArrives() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val source = FakePressRaceRankings()
                val loaded = rankingState()
                val cached = requireNotNull(loaded.all).toRankingSnapshot()
                val ready =
                    PressRaceComparisonState(
                        epoch = 1L,
                        view = PressRaceView.ALL,
                        phase = PressRacePhase.Ready,
                        snapshot = cached,
                    )
                val comparisonState = loaded.updateComparison(ready)
                assertNull(comparisonState.all)
                assertEquals(cached, comparisonState.ranking(PressRaceView.ALL))
                source.state.value = comparisonState

                val vm = PressRankingViewModel(source)
                store.put("comparison-refresh", vm)
                runCurrent()
                assertFalse(vm.uiState.value.isRefreshing)

                vm.onRefresh()
                assertTrue(vm.uiState.value.isRefreshing)
                assertEquals(listOf(PressRaceView.ALL), source.refreshed)

                val waiting = ready.copy(phase = PressRacePhase.Initializing, snapshot = null)
                source.state.value = comparisonState.updateComparison(waiting)
                runCurrent()
                assertTrue(vm.uiState.value.isRefreshing)

                val next = cached.copy(generatedAt = "2026-10-27T00:00:01Z")
                source.state.value = source.state.value.updateComparison(ready.copy(snapshot = next))
                runCurrent()
                assertFalse(vm.uiState.value.isRefreshing)
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
