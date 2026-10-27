package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModelStore
import com.pheeeew.data.press.race.FakePressRaceRankings
import com.pheeeew.data.press.race.PressRaceNoticeState
import com.pheeeew.data.press.race.PressRaceOvertakes
import com.pheeeew.data.press.race.PressRaceParticipant
import com.pheeeew.data.press.race.PressRacePhase
import com.pheeeew.data.press.race.PressRaceRankingState
import com.pheeeew.data.press.race.PressRaceReady
import com.pheeeew.data.press.race.PressRaceSessionState
import com.pheeeew.data.press.race.PressRaceSnapshot
import com.pheeeew.data.press.race.PressRaceView
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressSessionState
import com.pheeeew.domain.repository.PressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PressNoticeViewModelTest {
    @Test fun recreationSkipsOldNoticeAndRepositoryUpdatesDoNotCreateNewOccurrences() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val repository = NoticeRepository()
                val rankings = FakePressRaceRankings()
                rankings.state.value = ranking(1)
                val model = PressViewModel(repository, rankings)
                store.put("press", model)
                runCurrent()
                assertNull(model.uiState.value.activity.notice)
                rankings.state.value = ranking(2)
                runCurrent()
                val notice = model.uiState.value.activity.notice!!
                assertEquals(2L, notice.displayId)
                assertEquals(2L, notice.overtaken)
                assertEquals(3L, notice.overtakenBy)
                repository.state.value = repository.state.value.copy(pendingPressCount = 1)
                runCurrent()
                assertEquals(notice, model.uiState.value.activity.notice)
                rankings.state.value =
                    rankings.state.value.copy(
                        session =
                            rankings.state.value.session
                                .copy(notice = null),
                    )
                runCurrent()
                assertNull(model.uiState.value.activity.notice)
                rankings.state.value = ranking(3)
                runCurrent()
                assertEquals(
                    3L,
                    model.uiState.value.activity.notice
                        ?.displayId,
                )
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    private fun ranking(id: Long): PressRaceRankingState {
        val snapshot =
            PressRaceSnapshot(
                "s",
                "c",
                "2026-10-27",
                "$id",
                "2026-10-27T00:00:00Z",
                PressRaceView.PRESS,
                2,
                PressRaceParticipant("me", "name", 5, 2, true),
                emptyList(),
                emptyList(),
                false,
                PressRaceOvertakes(2, 3),
            )
        return PressRaceRankingState(
            session =
                PressRaceSessionState(
                    phase = PressRacePhase.Ready,
                    view = PressRaceView.PRESS,
                    ready = PressRaceReady("s", "c", snapshot.date, emptyMap(), emptyList()),
                    snapshot = snapshot,
                    notice = PressRaceNoticeState(id, false, 2, 3, 2_000),
                ),
            press = snapshot,
        )
    }
}

private class NoticeRepository : PressRepository {
    override val state = MutableStateFlow(PressSessionState())

    override fun accept(emotion: EmotionState) = PressAcceptance.Accepted

    override fun refreshToday(force: Boolean) = Unit

    override fun retryUnsent() = Unit

    override fun onForeground() = Unit
}
