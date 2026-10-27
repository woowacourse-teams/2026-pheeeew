package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.data.press.race.PressRaceFrameKey
import com.pheeeew.data.press.race.PressRaceMeasurements
import com.pheeeew.data.press.race.PressRaceRankings
import com.pheeeew.data.press.race.PressRaceView
import com.pheeeew.data.press.race.frameKey
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.repository.PressRepository
import com.pheeeew.feature.emotion.model.EmotionKind
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Owns only this screen's projection; durable-for-session data and work live in PressRepository. */
@Inject
internal class PressViewModel(
    private val repository: PressRepository,
    rankings: PressRaceRankings,
    private val measurements: PressRaceMeasurements = PressRaceMeasurements(),
) : ViewModel() {
    // A recreated screen may borrow ranking cache but never replays an already published alert.
    private val initialFrameKey =
        rankings.state.value.session.currentSnapshot
            ?.frameKey()
    private val initialNoticeId =
        rankings.state.value.session.notice
            ?.id

    // Project the session cache synchronously so a recreated screen never flashes its empty default state.
    private val mutableUiState =
        MutableStateFlow(
            repository.state.value.toUiState().copy(
                nearbyRanking = rankings.state.value.toNearbyRanking(),
                activity =
                    rankings.state.value
                        .toActivity()
                        .copy(notice = null),
            ),
        )
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.state, rankings.state) { sessionState, ranking ->
                sessionState.toUiState().copy(
                    frameKey =
                        ranking.session.currentSnapshot
                            ?.takeIf { measurements.isEnabled }
                            ?.takeIf { it.view == PressRaceView.PRESS }
                            ?.frameKey()
                            ?.takeUnless { it == initialFrameKey },
                    nearbyRanking = ranking.toNearbyRanking(),
                    activity =
                        ranking.toActivity().let { activity ->
                            activity.copy(notice = activity.notice?.takeUnless { it.displayId == initialNoticeId })
                        },
                )
            }.collect {
                mutableUiState.value = it
                it.frameKey?.let(measurements::uiApplied)
            }
        }
    }

    fun onFrameDrawn(key: PressRaceFrameKey) = measurements.drawn(key)

    fun onScreenResumed() {
        repository.refreshToday()
        repository.retryUnsent()
    }

    fun retryUnsent() = repository.retryUnsent()

    fun onEmotionTap(emotion: EmotionKind): Boolean =
        repository.accept(emotion.toDomainState()) == PressAcceptance.Accepted
}
