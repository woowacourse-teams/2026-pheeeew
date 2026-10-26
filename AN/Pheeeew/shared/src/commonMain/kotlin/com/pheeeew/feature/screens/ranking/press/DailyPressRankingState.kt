package com.pheeeew.feature.screens.ranking.press

import com.pheeeew.core.network.ApiResult
import com.pheeeew.domain.model.ranking.DailyPressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DailyPressRankingUiState(
    val date: String? = null,
    val ranking: PressRankingUiState = PressRankingUiState(),
)

/** Composition-owned state. A new notification refreshes even if its date has not changed. */
internal class DailyPressRankingState(
    private val source: suspend (String) -> ApiResult<DailyPressRanking>,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(DailyPressRankingUiState())
    val state = mutableState.asStateFlow()
    private var selection: Pair<String, String>? = null
    private var requestJob: Job? = null
    private var revision = 0L

    fun select(
        date: String,
        notificationId: String,
    ): Boolean {
        val next = date to notificationId
        if (selection == next) return false
        selection = next
        load(date, preserveContent = false)
        return true
    }

    fun refresh() {
        val date = selection?.first ?: return
        if (requestJob?.isActive == true) return
        load(date, preserveContent = state.value.ranking.status == PressRankingStatus.Ready)
    }

    fun retry() {
        if (state.value.ranking.status == PressRankingStatus.Failed) refresh()
    }

    fun close() {
        revision++
        requestJob?.cancel()
    }

    private fun load(
        date: String,
        preserveContent: Boolean,
    ) {
        requestJob?.cancel()
        val requestedRevision = ++revision
        mutableState.value =
            DailyPressRankingUiState(
                date,
                if (preserveContent) {
                    state.value.ranking.copy(isRefreshing = true, hasRefreshError = false)
                } else {
                    PressRankingUiState()
                },
            )
        requestJob =
            scope.launch {
                try {
                    when (val result = source(date)) {
                        is ApiResult.Success -> {
                            if (requestedRevision != revision) return@launch
                            check(result.value.date == date) { "Ranking date changed" }
                            mutableState.value =
                                DailyPressRankingUiState(
                                    date,
                                    PressRankingUiState(
                                        weekLabel = date,
                                        people = result.value.items.map { it.toPerson() },
                                        me = result.value.me?.toPerson(),
                                        status = PressRankingStatus.Ready,
                                    ),
                                )
                        }

                        is ApiResult.Failure -> {
                            fail(requestedRevision, preserveContent)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    fail(requestedRevision, preserveContent)
                }
            }
    }

    private fun fail(
        requestedRevision: Long,
        preserveContent: Boolean,
    ) {
        if (requestedRevision != revision) return
        mutableState.value =
            state.value.copy(
                ranking =
                    if (preserveContent) {
                        state.value.ranking.copy(isRefreshing = false, hasRefreshError = true)
                    } else {
                        PressRankingUiState(status = PressRankingStatus.Failed)
                    },
            )
    }

    private fun PressRankingItem.toPerson() = PressPersonRank(rank, nickname, pressCount)
}
