package com.pheeeew.feature.screens.ranking.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.utils.withMinimumLoadingTime
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.resultLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal data class PressRankingUiState(
    val selectedEmotion: PressEmotion = PressEmotion.All,
    val weeksAgo: Int = 0,
    val weekLabel: String = koreanWeekRangeLabel(),
    val hasPrevious: Boolean = false,
    val groups: List<PressGroupRank> = emptyList(),
    val status: PressRankingStatus = PressRankingStatus.Loading,
    val isRefreshing: Boolean = false,
    val hasRefreshError: Boolean = false,
)

internal enum class PressRankingStatus {
    Loading,
    Ready,
    Failed,
}

internal class PressRankingViewModel(
    private val source: PressRankingSource,
    monitoring: com.pheeeew.core.monitoring.Monitoring = com.pheeeew.core.monitoring.NoOpMonitoring,
) : ViewModel() {
    val telemetry = ProductMonitoring(monitoring, "ranking")
    private val _uiState = MutableStateFlow(PressRankingUiState())
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var requestGeneration = 0L

    init {
        load(PressEmotion.All, weeksAgo = 0)
    }

    fun onEmotionSelected(emotion: PressEmotion) {
        if (emotion != _uiState.value.selectedEmotion) load(emotion, _uiState.value.weeksAgo)
    }

    fun onPreviousWeek() {
        val state = _uiState.value
        if (state.status == PressRankingStatus.Ready && state.hasPrevious) {
            load(state.selectedEmotion, state.weeksAgo + 1)
        }
    }

    fun onNextWeek() {
        val state = _uiState.value
        if (state.status == PressRankingStatus.Ready && state.weeksAgo > 0) {
            load(state.selectedEmotion, state.weeksAgo - 1)
        }
    }

    fun onRetry() {
        val state = _uiState.value
        if (state.status == PressRankingStatus.Failed) load(state.selectedEmotion, state.weeksAgo)
    }

    fun onRefresh() {
        if (requestJob?.isActive == true) return
        val state = _uiState.value
        load(state.selectedEmotion, state.weeksAgo, preserveContent = state.status == PressRankingStatus.Ready)
    }

    private fun load(
        emotion: PressEmotion,
        weeksAgo: Int,
        preserveContent: Boolean = false,
    ) {
        requestJob?.cancel()
        val requestId = ++requestGeneration
        val requestedWeekLabel = koreanWeekRangeLabel(weeksAgo)
        _uiState.value =
            if (preserveContent) {
                _uiState.value.copy(
                    selectedEmotion = emotion,
                    weeksAgo = weeksAgo,
                    isRefreshing = true,
                    hasRefreshError = false,
                )
            } else {
                PressRankingUiState(selectedEmotion = emotion, weeksAgo = weeksAgo, weekLabel = requestedWeekLabel)
            }
        requestJob =
            viewModelScope.launch {
                try {
                    when (
                        val result =
                            withMinimumLoadingTime {
                                telemetry
                                    .operation(
                                        "press_ranking_load_finished",
                                        mapOf(
                                            "weeks_ago" to
                                                com.pheeeew.core.monitoring.EventValue
                                                    .Integer(weeksAgo.toLong()),
                                        ),
                                    ).observe(::resultLabel) { source.load(emotion, weeksAgo) }
                            }
                    ) {
                        is PressRankingLoadResult.Loaded -> {
                            if (requestId != requestGeneration) return@launch
                            _uiState.value =
                                PressRankingUiState(
                                    selectedEmotion = emotion,
                                    weeksAgo = weeksAgo,
                                    weekLabel = formatWeekRange(result.startAt, result.endAt),
                                    hasPrevious = result.hasPrevious,
                                    groups = result.groups,
                                    status = PressRankingStatus.Ready,
                                )
                        }

                        PressRankingLoadResult.Unavailable -> {
                            if (requestId == requestGeneration) {
                                _uiState.value =
                                    if (preserveContent) {
                                        _uiState.value.copy(isRefreshing = false, hasRefreshError = true)
                                    } else {
                                        PressRankingUiState(
                                            selectedEmotion = emotion,
                                            weeksAgo = weeksAgo,
                                            weekLabel = requestedWeekLabel,
                                            status = PressRankingStatus.Failed,
                                        )
                                    }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (requestId == requestGeneration) {
                        _uiState.value =
                            if (preserveContent) {
                                _uiState.value.copy(isRefreshing = false, hasRefreshError = true)
                            } else {
                                PressRankingUiState(
                                    selectedEmotion = emotion,
                                    weeksAgo = weeksAgo,
                                    weekLabel = requestedWeekLabel,
                                    status = PressRankingStatus.Failed,
                                )
                            }
                    }
                } finally {
                    if (requestId == requestGeneration) requestJob = null
                }
            }
    }
}

private fun formatWeekRange(
    startAt: String,
    endAt: String,
): String = "${startAt.toKoreanDate()} ~ ${endAt.toKoreanDate()}"

private fun String.toKoreanDate(): String =
    runCatching {
        Instant
            .parse(this)
            .plus(9.hours)
            .toString()
            .take(10)
            .replace('-', '.')
    }.getOrElse { take(10).replace('-', '.') }

private const val KOREA_OFFSET_MILLIS = 9 * 60 * 60 * 1_000L
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000L

private fun koreanWeekRangeLabel(weeksAgo: Int = 0): String {
    val todayInKorea = (Clock.System.now().toEpochMilliseconds() + KOREA_OFFSET_MILLIS) / MILLIS_PER_DAY
    val daysSinceMonday = ((todayInKorea + 3) % 7 + 7) % 7
    val monday = todayInKorea - daysSinceMonday - weeksAgo * 7L
    return "${dateFromEpochDay(monday)} ~ ${dateFromEpochDay(monday + 7)}"
}

private fun dateFromEpochDay(epochDay: Long): String =
    Instant
        .fromEpochMilliseconds(epochDay * MILLIS_PER_DAY)
        .toString()
        .take(10)
        .replace('-', '.')
