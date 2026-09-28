package com.pheeeew.feature.screens.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.resultLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class WeeklyRankingUiState(
    val weeksAgo: Int = 0,
    val weekLabel: String = "",
    val hasPrevious: Boolean = false,
    val rankings: List<RankingMember> = emptyList(),
    val status: WeeklyRankingStatus = WeeklyRankingStatus.Loading,
)

enum class WeeklyRankingStatus {
    Loading,
    Ready,
    Failed,
}

class WeeklyRankingViewModel(
    private val source: WeeklyRankingSource,
    monitoring: com.pheeeew.core.monitoring.Monitoring = com.pheeeew.core.monitoring.NoOpMonitoring,
) : ViewModel() {
    val telemetry = ProductMonitoring(monitoring, "ranking")
    private val _uiState = MutableStateFlow(WeeklyRankingUiState())
    val uiState = _uiState.asStateFlow()

    private var requestJob: Job? = null
    private var requestGeneration = 0L

    init {
        load(weeksAgo = 0)
    }

    fun onPreviousWeek() {
        val state = _uiState.value
        if (state.status != WeeklyRankingStatus.Ready || !state.hasPrevious) return
        telemetry.emit(
            "ranking_week_changed",
            mapOf(
                "from_weeks_ago" to
                    com.pheeeew.core.monitoring.EventValue
                        .Integer(state.weeksAgo.toLong()),
                "weeks_ago" to
                    com.pheeeew.core.monitoring.EventValue
                        .Integer((state.weeksAgo + 1).toLong()),
            ),
        )
        load(state.weeksAgo + 1)
    }

    fun onNextWeek() {
        val state = _uiState.value
        if (state.status != WeeklyRankingStatus.Ready || state.weeksAgo == 0) return
        telemetry.emit(
            "ranking_week_changed",
            mapOf(
                "from_weeks_ago" to
                    com.pheeeew.core.monitoring.EventValue
                        .Integer(state.weeksAgo.toLong()),
                "weeks_ago" to
                    com.pheeeew.core.monitoring.EventValue
                        .Integer((state.weeksAgo - 1).toLong()),
            ),
        )
        load(state.weeksAgo - 1)
    }

    fun onRetry() {
        if (_uiState.value.status == WeeklyRankingStatus.Failed) load(_uiState.value.weeksAgo)
    }

    private fun load(weeksAgo: Int) {
        requestJob?.cancel()
        val requestId = ++requestGeneration
        val currentWeekLabel = _uiState.value.weekLabel
        _uiState.value = WeeklyRankingUiState(weeksAgo = weeksAgo, weekLabel = currentWeekLabel)
        requestJob =
            viewModelScope.launch {
                try {
                    when (
                        val result =
                            telemetry
                                .operation(
                                    "ranking_load_finished",
                                    mapOf(
                                        "weeks_ago" to
                                            com.pheeeew.core.monitoring.EventValue
                                                .Integer(weeksAgo.toLong()),
                                    ),
                                ).observe(::resultLabel) { source.load(weeksAgo) }
                    ) {
                        is WeeklyRankingLoadResult.Loaded -> {
                            if (requestId != requestGeneration) return@launch
                            _uiState.value =
                                WeeklyRankingUiState(
                                    weeksAgo = weeksAgo,
                                    weekLabel = formatWeekRange(result.startAt, result.endAt),
                                    hasPrevious = result.hasPrevious,
                                    rankings = result.rankings,
                                    status = WeeklyRankingStatus.Ready,
                                )
                        }

                        WeeklyRankingLoadResult.Unavailable -> {
                            if (requestId == requestGeneration) {
                                _uiState.value =
                                    WeeklyRankingUiState(
                                        weeksAgo = weeksAgo,
                                        weekLabel = currentWeekLabel,
                                        status = WeeklyRankingStatus.Failed,
                                    )
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (requestId == requestGeneration) {
                        _uiState.value =
                            WeeklyRankingUiState(
                                weeksAgo = weeksAgo,
                                weekLabel = currentWeekLabel,
                                status = WeeklyRankingStatus.Failed,
                            )
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
