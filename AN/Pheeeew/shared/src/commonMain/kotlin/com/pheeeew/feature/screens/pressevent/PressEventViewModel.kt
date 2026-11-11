package com.pheeeew.feature.screens.pressevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.pressevent.PressEventAttempt
import com.pheeeew.domain.model.pressevent.PressEventGame
import com.pheeeew.domain.model.pressevent.PressEventGamePhase
import com.pheeeew.domain.model.pressevent.PressEventMyRank
import com.pheeeew.domain.repository.PressEventFailure
import com.pheeeew.domain.repository.PressEventRepository
import com.pheeeew.domain.repository.PressEventResult
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.toDomainState
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@AssistedInject
internal class PressEventViewModel(
    private val repository: PressEventRepository,
    @Assisted private var eventId: String?,
    @Assisted private var sessionGeneration: Long?,
    @Assisted nowMillis: () -> Long,
) : ViewModel() {
    @AssistedFactory
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            eventId: String?,
            sessionGeneration: Long?,
            nowMillis: () -> Long = monotonicPressEventClock(),
        ): PressEventViewModel
    }

    private val game = PressEventGame(nowMillis)
    private val mutableUiState =
        MutableStateFlow(PressEventViewState(game = game.state.toUiState(), configured = configured()))
    val uiState = mutableUiState.asStateFlow()
    private val mutableRemainingMillis = MutableStateFlow(game.remainingMillis)
    val remainingMillis = mutableRemainingMillis.asStateFlow()
    private var timerJob: Job? = null
    private var rankingJob: Job? = null
    private var submissionJob: Job? = null
    private var screenVisible = false
    private var revision = 0L
    private var rankingRequest = 0L
    private var requestId: String? = null
    private var completedAttempt: PressEventAttempt? = null

    fun onContextChanged(
        id: String?,
        generation: Long?,
    ) {
        if (eventId == id && sessionGeneration == generation) return
        revision++
        rankingRequest++
        timerJob?.cancel()
        rankingJob?.cancel()
        submissionJob?.cancel()
        eventId = id
        sessionGeneration = generation
        completedAttempt = null
        requestId = null
        game.reset()
        mutableUiState.value = PressEventViewState(game = game.state.toUiState(), configured = configured())
        mutableRemainingMillis.value = game.remainingMillis
    }

    fun onScreenResumed() {
        screenVisible = true
        refreshRankings()
    }

    fun start() {
        if (!mutableUiState.value.canStart || !game.start()) return
        val before = mutableUiState.value
        val previous = before.result?.let { PressEventMyRank(it.bestScore, it.rank) } ?: before.me
        timerJob?.cancel()
        completedAttempt = null
        requestId = Uuid.random().toString()
        mutableUiState.value =
            mutableUiState.value.copy(
                submission = EventSubmissionStatus.None,
                result = null,
                previousResult = previous,
                previousResultKnown = before.result != null || before.rankedAt != null,
            )
        publishGame()
        timerJob =
            viewModelScope.launch {
                while (game.state.isActive) {
                    delay(25L)
                    game.tick()
                    publishGame()
                    handleAttemptFinished()
                }
            }
    }

    fun press(kind: EmotionKind): Boolean {
        val accepted = game.press(kind.toDomainState())
        publishGame()
        handleAttemptFinished()
        return accepted
    }

    fun returnToRanking() {
        timerJob?.cancel()
        submissionJob?.cancel()
        submissionJob = null
        completedAttempt = null
        requestId = null
        game.reset()
        mutableUiState.value = mutableUiState.value.copy(submission = EventSubmissionStatus.None, result = null)
        publishGame()
        refreshRankings()
    }

    fun onScreenHidden() {
        screenVisible = false
        timerJob?.cancel()
        rankingJob?.cancel()
        rankingRequest++
        game.tick()
        handleAttemptFinished()
        game.cancelActiveAttempt()
        publishGame()
        mutableUiState.value = mutableUiState.value.copy(rankingLoading = false, loadingMore = false)
    }

    fun retrySubmission() {
        if (game.state.phase != PressEventGamePhase.Finished ||
            !mutableUiState.value.submission.canRetrySubmission
        ) {
            return
        }
        submitCompletedAttempt()
    }

    /** Project state only; completion processing is an explicit caller action. */
    private fun publishGame() {
        mutableUiState.value = mutableUiState.value.copy(game = game.state.toUiState())
        mutableRemainingMillis.value = game.remainingMillis
    }

    private fun handleAttemptFinished() {
        if (game.state.phase != PressEventGamePhase.Finished || completedAttempt != null) return
        val id = requestId ?: return
        completedAttempt = PressEventAttempt(id, game.state.counts)
        submitCompletedAttempt()
    }

    private fun submitCompletedAttempt() {
        val attempt = completedAttempt ?: return
        val id = eventId?.takeIf { it.isNotBlank() } ?: return
        val generation = sessionGeneration ?: return
        if (mutableUiState.value.submission == EventSubmissionStatus.Submitting) return
        val currentRevision = revision
        mutableUiState.value = mutableUiState.value.copy(submission = EventSubmissionStatus.Submitting)
        submissionJob?.cancel()
        submissionJob =
            viewModelScope.launch {
                val result = repository.submit(id, attempt, generation)
                if (currentRevision != revision || completedAttempt !== attempt) return@launch
                when (result) {
                    is PressEventResult.Success -> {
                        mutableUiState.value =
                            mutableUiState.value.copy(
                                submission = EventSubmissionStatus.Submitted,
                                result = result.value,
                            )
                        if (screenVisible) loadRankings(cursor = null)
                    }

                    is PressEventResult.Failure -> {
                        val status =
                            when (result.reason) {
                                PressEventFailure.Conflict -> EventSubmissionStatus.Conflict
                                PressEventFailure.Rejected -> EventSubmissionStatus.Rejected
                                else -> EventSubmissionStatus.Failed
                            }
                        mutableUiState.value = mutableUiState.value.copy(submission = status)
                    }
                }
            }
    }

    fun refreshRankings() {
        if (!configured() || rankingJob?.isActive == true) return
        loadRankings(cursor = null)
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (rankingJob?.isActive == true) return
        val cursor = state.nextCursor ?: return
        loadRankings(cursor)
    }

    private fun loadRankings(cursor: String?) {
        val id = eventId?.takeIf { it.isNotBlank() } ?: return
        val generation = sessionGeneration ?: return
        rankingJob?.cancel()
        val currentRevision = revision
        val request = ++rankingRequest
        mutableUiState.value =
            mutableUiState.value.copy(
                rankingLoading = false,
                loadingMore = cursor != null,
                rankingError = false,
                nextPageError = false,
            )
        rankingJob =
            viewModelScope.launch {
                var loadingShown = false
                val loadingIndicator =
                    if (cursor == null) {
                        launch {
                            delay(LOADING_INDICATOR_DELAY_MILLIS)
                            loadingShown = true
                            mutableUiState.value = mutableUiState.value.copy(rankingLoading = true)
                            delay(LOADING_INDICATOR_MINIMUM_MILLIS)
                        }
                    } else {
                        null
                    }
                val result = repository.rankings(id, generation, cursor = cursor)
                if (loadingShown) loadingIndicator?.join() else loadingIndicator?.cancel()
                if (currentRevision != revision || request != rankingRequest) return@launch
                val current = mutableUiState.value
                mutableUiState.value =
                    when (result) {
                        is PressEventResult.Success -> {
                            val page = result.value
                            val people =
                                page.items.map {
                                    PressPersonRank(
                                        it.rank,
                                        it.nickname,
                                        it.bestScore,
                                        it.publicId,
                                        it.mine,
                                    )
                                }
                            current.copy(
                                leader =
                                    page.leader?.let {
                                        PressPersonRank(
                                            1L,
                                            it.nickname,
                                            it.bestScore,
                                            it.publicId,
                                        )
                                    },
                                people =
                                    if (cursor ==
                                        null
                                    ) {
                                        people
                                    } else {
                                        (current.people + people).distinctBy { it.publicId }
                                    },
                                me = page.me,
                                rankedAt = page.rankedAt,
                                nextCursor = page.nextCursor,
                                rankingLoading = false,
                                loadingMore = false,
                            )
                        }

                        is PressEventResult.Failure -> {
                            current.copy(
                                rankingLoading = false,
                                loadingMore = false,
                                rankingError =
                                    cursor == null,
                                nextPageError = cursor != null,
                            )
                        }
                    }
            }
    }

    private fun configured(): Boolean = !eventId.isNullOrBlank() && sessionGeneration != null

    private companion object {
        const val LOADING_INDICATOR_DELAY_MILLIS = 300L
        const val LOADING_INDICATOR_MINIMUM_MILLIS = 500L
    }
}
