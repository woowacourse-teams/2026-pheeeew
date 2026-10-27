package com.pheeeew.feature.screens.ranking.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.repository.DailyPressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingRepository
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AssistedInject
internal class DailyPressRankingViewModel(
    private val repository: PressRankingRepository,
    @Assisted private val date: String,
    @Assisted private var sessionGeneration: Long?,
) : ViewModel() {
    @AssistedFactory
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            date: String,
            sessionGeneration: Long?,
        ): DailyPressRankingViewModel
    }

    private val mutableUiState =
        MutableStateFlow(
            PressRankingUiState(
                date = date,
                status = if (sessionGeneration == null) PressRankingStatus.Failed else PressRankingStatus.Loading,
            ),
        )
    val uiState = mutableUiState.asStateFlow()
    private var requestId = 0L
    private var requestJob: Job? = null
    private var notificationRefreshPending = false

    init {
        refresh()
    }

    fun onSessionChanged(generation: Long?) {
        if (sessionGeneration == generation) return
        val previous = requestJob
        requestId++
        notificationRefreshPending = false
        requestJob = null
        sessionGeneration = generation
        previous?.cancel()
        mutableUiState.value =
            PressRankingUiState(
                date = date,
                status = if (generation == null) PressRankingStatus.Failed else PressRankingStatus.Loading,
            )
        if (generation != null) refresh()
    }

    fun onRefresh() {
        if (requestJob?.isActive == true) return
        refresh(showIndicator = true)
    }

    fun onResumed() {
        if (requestJob?.isActive == true) return
        refresh()
    }

    fun onNotificationReceived() {
        if (requestJob?.isActive == true) {
            notificationRefreshPending = true
            return
        }
        refresh()
    }

    fun onRetry() {
        if (mutableUiState.value.status == PressRankingStatus.Failed) refresh()
    }

    private fun refresh(showIndicator: Boolean = false) {
        val generation = sessionGeneration ?: return
        val request = ++requestId
        val previous = requestJob
        requestJob = null
        previous?.cancel()
        val hasContent = mutableUiState.value.status == PressRankingStatus.Ready
        mutableUiState.value =
            if (hasContent) {
                mutableUiState.value.copy(isRefreshing = showIndicator, hasRefreshError = false)
            } else {
                PressRankingUiState(date = date)
            }
        requestJob =
            viewModelScope.launch {
                try {
                    when (val result = repository.findDaily(date, generation)) {
                        is DailyPressRankingLoadResult.Loaded -> {
                            if (request != requestId || generation != sessionGeneration) return@launch
                            val ranking = result.ranking
                            mutableUiState.value =
                                PressRankingUiState(
                                    date = ranking.date,
                                    people =
                                        ranking.items.map {
                                            PressPersonRank(
                                                it.rank.toLong(),
                                                it.nickname,
                                                it.pressCount,
                                            )
                                        },
                                    me =
                                        ranking.me?.let {
                                            PressPersonRank(
                                                it.rank.toLong(),
                                                it.nickname,
                                                it.pressCount,
                                            )
                                        },
                                    status = PressRankingStatus.Ready,
                                )
                        }

                        DailyPressRankingLoadResult.Unavailable -> {
                            if (request == requestId && generation == sessionGeneration) fail(hasContent)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (request == requestId && generation == sessionGeneration) fail(hasContent)
                } finally {
                    if (request == requestId && generation == sessionGeneration) {
                        requestJob = null
                        if (isActive && notificationRefreshPending) {
                            notificationRefreshPending = false
                            refresh()
                        }
                    }
                }
            }
    }

    private fun fail(hasContent: Boolean) {
        mutableUiState.value =
            if (hasContent) {
                mutableUiState.value.copy(isRefreshing = false, hasRefreshError = true)
            } else {
                PressRankingUiState(date = date, status = PressRankingStatus.Failed)
            }
    }
}
