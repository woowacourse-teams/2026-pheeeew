package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressRepository
import com.pheeeew.domain.repository.press.PressSubmitResult
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal data class PressUiState(
    val myToday: MyDailyPressSnapshot? = null,
    val allToday: AllDailyPressSnapshot? = null,
    val isLoadingMy: Boolean = true,
    val isLoadingAll: Boolean = true,
    val hasMyError: Boolean = false,
    val hasAllError: Boolean = false,
    val isSending: Boolean = false,
    val pendingPressCount: Int = 0,
    val notice: PressNotice? = null,
) {
    val myEmotionCounts
        get() =
            myToday
                ?.counts
                ?.mapKeys { (emotion, _) ->
                    when (emotion) {
                        EmotionState.FRUSTRATED -> EmotionKind.Blocked
                        EmotionState.IRRITATED -> EmotionKind.Annoyed
                        EmotionState.EXHAUSTED -> EmotionKind.Tired
                        EmotionState.DISCOURAGED -> EmotionKind.Defeated
                        EmotionState.ANGRY -> EmotionKind.Angry
                    }
                }.orEmpty()
}

internal enum class PressNotice {
    LocationPreparing,
    LocationUnavailable,
    QueueFull,
    Rejected,
    OutcomeUnknown,
}

/** App-session owner for personal press reads and accepted writes. */
internal class PressViewModel(
    private val repository: PressRepository,
    locationDependencies: LocationDependencies,
) : ViewModel() {
    private val locationRepository: LocationRepository = locationDependencies.repository
    private val refreshLocation =
        RefreshLocationUseCase(
            permissionController = locationDependencies.permissionController,
            repository = locationDependencies.repository,
        )
    private val _uiState = MutableStateFlow(PressUiState())
    val uiState = _uiState.asStateFlow()

    private val batch = PressBatchAccumulator(MAX_ACCEPTED_OUTSTANDING)
    private val locationPendingCounts = EmotionState.entries.associateWith { 0 }.toMutableMap()
    private var outstandingCount = 0
    private var locationPendingCount = 0
    private var inFlightCount = 0
    private var isWriting = false
    private var flushJob: Job? = null
    private var locationJob: Job? = null
    private var statisticsJob: Job? = null
    private var statisticsRefreshDelayJob: Job? = null
    private var statisticsDirty = true
    private var statisticsLoadedAtMillis: Long? = null
    private var lastBatchEmotionIndex = 0
    private var refreshAgainAfterCurrentRead = false

    fun onScreenResumed() {
        val lastReadAt = statisticsLoadedAtMillis
        val now = Clock.System.now().toEpochMilliseconds()
        if (lastReadAt == null || now - lastReadAt >= STATISTICS_TTL_MILLIS || statisticsDirty) {
            refreshStatistics(force = true)
        }
    }

    fun refreshStatistics(force: Boolean = false) {
        if (statisticsJob?.isActive == true) {
            refreshAgainAfterCurrentRead = true
            return
        }
        val lastReadAt = statisticsLoadedAtMillis
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && !statisticsDirty && lastReadAt != null && now - lastReadAt < STATISTICS_TTL_MILLIS) return

        statisticsDirty = false
        statisticsJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLoadingMy = true,
                        isLoadingAll = true,
                        hasMyError = false,
                        hasAllError = false,
                    )
                }
                val initial = fetchStatisticsPair()
                val aligned =
                    if (initial.hasDifferentServerDates()) {
                        fetchStatisticsPair()
                    } else {
                        initial
                    }
                val datesMatch = !aligned.hasDifferentServerDates()
                _uiState.update { current ->
                    val my = (aligned.my as? PressReadResult.Loaded)?.value
                    val all = (aligned.all as? PressReadResult.Loaded)?.value
                    current.copy(
                        myToday = if (datesMatch) my ?: current.myToday else null,
                        allToday = if (datesMatch) all ?: current.allToday else null,
                        isLoadingMy = false,
                        isLoadingAll = false,
                        hasMyError = my == null || !datesMatch,
                        hasAllError = all == null || !datesMatch,
                    )
                }
                statisticsLoadedAtMillis = Clock.System.now().toEpochMilliseconds()
                statisticsDirty = refreshAgainAfterCurrentRead
                refreshAgainAfterCurrentRead = false
                if (statisticsDirty) scheduleStatisticsRefresh()
            }
    }

    /** Returns true only when the press was accepted into the bounded in-memory queue. */
    fun onEmotionTap(emotion: EmotionKind): Boolean {
        val currentLocation = currentFreshLocation()
        if (currentLocation != null) acceptLocationPending(currentLocation)
        if (currentLocation == null) {
            if (outstandingCount >= MAX_ACCEPTED_OUTSTANDING) {
                _uiState.update { it.copy(notice = PressNotice.QueueFull) }
                return false
            }
            val state = emotion.toDomainState()
            locationPendingCounts[state] = locationPendingCounts.getValue(state) + 1
            locationPendingCount++
            outstandingCount++
            _uiState.update {
                it.copy(
                    pendingPressCount = outstandingCount,
                    notice = PressNotice.LocationPreparing,
                )
            }
            requestLocation()
            return true
        }
        if (outstandingCount >= MAX_ACCEPTED_OUTSTANDING) {
            _uiState.update { it.copy(notice = PressNotice.QueueFull) }
            return false
        }

        val accepted = batch.add(emotion.toDomainState(), currentLocation)
        if (!accepted) {
            _uiState.update { it.copy(notice = PressNotice.QueueFull) }
            return false
        }
        outstandingCount += 1
        _uiState.update { it.copy(pendingPressCount = outstandingCount, notice = null) }
        scheduleFlush(if (batch.isReadyToFlush()) 0L else BATCH_FLUSH_DELAY_MILLIS)
        return true
    }

    fun retryLocation() {
        if (locationPendingCount == 0) return
        _uiState.update { it.copy(notice = PressNotice.LocationPreparing) }
        requestLocation()
    }

    private fun currentFreshLocation(): CurrentLocation? {
        val current = (locationRepository.state.value as? LocationState.Available)?.location ?: return null
        val age = Clock.System.now().toEpochMilliseconds() - current.capturedAtMillis
        return current.takeIf { age in 0..MAX_LOCATION_AGE_MILLIS }
    }

    private fun requestLocation() {
        if (locationJob?.isActive == true) return
        locationJob =
            viewModelScope.launch {
                try {
                    refreshLocation(requestPermission = true)
                    val refreshedLocation = currentFreshLocation()
                    if (refreshedLocation == null) {
                        if (locationPendingCount > 0) {
                            _uiState.update { it.copy(notice = PressNotice.LocationUnavailable) }
                        }
                        return@launch
                    }
                    acceptLocationPending(refreshedLocation)
                    _uiState.update { it.copy(notice = null, pendingPressCount = outstandingCount) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (locationPendingCount > 0) {
                        _uiState.update { it.copy(notice = PressNotice.LocationUnavailable) }
                    }
                }
            }
    }

    private fun acceptLocationPending(location: CurrentLocation) {
        if (locationPendingCount == 0) return
        locationPendingCounts.forEach { (emotion, count) ->
            repeat(count) { check(batch.add(emotion, location)) }
        }
        EmotionState.entries.forEach { emotion -> locationPendingCounts[emotion] = 0 }
        locationPendingCount = 0
        scheduleFlush(if (batch.isReadyToFlush()) 0L else BATCH_FLUSH_DELAY_MILLIS)
    }

    private fun scheduleFlush(delayMillis: Long) {
        if (isWriting) return
        if (delayMillis == 0L) {
            flushJob?.cancel()
            flushJob = viewModelScope.launch { flushPending() }
        } else if (flushJob?.isActive != true) {
            flushJob =
                viewModelScope.launch {
                    delay(delayMillis)
                    flushPending()
                }
        }
    }

    private suspend fun flushPending() {
        if (isWriting) return
        val next = batch.take(MAX_EMOTION_BATCH_COUNT, MAX_TOTAL_BATCH_COUNT, lastBatchEmotionIndex) ?: return
        lastBatchEmotionIndex = (lastBatchEmotionIndex + 1) % EmotionState.entries.size
        isWriting = true
        inFlightCount = next.totalCount
        _uiState.update { it.copy(isSending = true) }
        val result =
            try {
                repository.submit(next.location, next.counts)
            } catch (cancelled: CancellationException) {
                // The server may have applied the request; this memory-only batch is never replayed.
                outstandingCount = (outstandingCount - inFlightCount).coerceAtLeast(0)
                inFlightCount = 0
                isWriting = false
                _uiState.update { it.copy(isSending = false, pendingPressCount = outstandingCount) }
                throw cancelled
            } catch (_: Exception) {
                PressSubmitResult.OutcomeUnknown
            }
        outstandingCount = (outstandingCount - inFlightCount).coerceAtLeast(0)
        inFlightCount = 0
        isWriting = false
        when (result) {
            PressSubmitResult.Submitted -> {
                statisticsDirty = true
                scheduleStatisticsRefresh()
            }

            PressSubmitResult.Rejected -> {
                _uiState.update { it.copy(notice = PressNotice.Rejected) }
            }

            PressSubmitResult.OutcomeUnknown -> {
                _uiState.update { it.copy(notice = PressNotice.OutcomeUnknown) }
                statisticsDirty = true
                scheduleStatisticsRefresh()
            }

            PressSubmitResult.Unavailable -> {
                _uiState.update { it.copy(notice = PressNotice.Rejected) }
            }
        }
        _uiState.update { it.copy(isSending = false, pendingPressCount = outstandingCount) }
        if (batch.isNotEmpty) scheduleFlush(if (batch.isReadyToFlush()) 0L else BATCH_FLUSH_DELAY_MILLIS)
    }

    private fun scheduleStatisticsRefresh() {
        if (statisticsRefreshDelayJob?.isActive == true) return
        if (statisticsJob?.isActive == true) {
            refreshAgainAfterCurrentRead = true
            return
        }
        statisticsRefreshDelayJob =
            viewModelScope.launch {
                delay(STATISTICS_AFTER_WRITE_DELAY_MILLIS)
                statisticsRefreshDelayJob = null
                refreshStatistics(force = true)
            }
    }

    private suspend fun fetchStatisticsPair(): StatisticsPair =
        coroutineScope {
            val my = async { repository.findMyToday() }
            val all = async { repository.findAllToday() }
            StatisticsPair(my.await(), all.await())
        }

    private fun StatisticsPair.hasDifferentServerDates(): Boolean {
        val myDate = (my as? PressReadResult.Loaded)?.value?.pressDate ?: return false
        val allDate = (all as? PressReadResult.Loaded)?.value?.pressDate ?: return false
        return myDate != allDate
    }

    private data class StatisticsPair(
        val my: PressReadResult<MyDailyPressSnapshot>,
        val all: PressReadResult<AllDailyPressSnapshot>,
    )

    private fun EmotionKind.toDomainState(): EmotionState =
        when (this) {
            EmotionKind.Blocked -> EmotionState.FRUSTRATED
            EmotionKind.Annoyed -> EmotionState.IRRITATED
            EmotionKind.Tired -> EmotionState.EXHAUSTED
            EmotionKind.Defeated -> EmotionState.DISCOURAGED
            EmotionKind.Angry -> EmotionState.ANGRY
        }

    private companion object {
        const val BATCH_FLUSH_DELAY_MILLIS = 500L
        const val STATISTICS_AFTER_WRITE_DELAY_MILLIS = 300L
        const val STATISTICS_TTL_MILLIS = 5_000L
        const val MAX_LOCATION_AGE_MILLIS = 60_000L
        const val MAX_ACCEPTED_OUTSTANDING = 300
        const val MAX_EMOTION_BATCH_COUNT = 30
        const val MAX_TOTAL_BATCH_COUNT = 100
    }
}
