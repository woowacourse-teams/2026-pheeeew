package com.pheeeew.feature.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.utils.withDelayedLoadingIndicator
import com.pheeeew.domain.model.collection.COLLECTION_PRESS_CLAIM_TARGET
import com.pheeeew.domain.model.collection.COLLECTION_STAMP_CLAIM_TARGET
import com.pheeeew.domain.model.collection.CollectionCard
import com.pheeeew.domain.model.collection.CollectionEmotion
import com.pheeeew.domain.model.collection.CollectionEmotionPage
import com.pheeeew.domain.model.collection.CollectionFailure
import com.pheeeew.domain.model.collection.CollectionHistoryQuery
import com.pheeeew.domain.model.collection.CollectionResult
import com.pheeeew.domain.model.collection.CollectionToday
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.CollectionRepository
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.calendar.currentCollectionMonth
import com.pheeeew.feature.screens.collection.calendar.detail.CollectionAudioLoader
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

internal data class CollectionUiState(
    val nickname: String? = null,
    val cards: List<CollectionCardUiModel> = emptyList(),
    val totalCount: Long = 0,
    val emotionCounts: Map<EmotionType, Long> = emptyMap(),
    val selectedFilter: EmotionType? = null,
    val today: CollectionToday? = null,
    val isLoadingToday: Boolean = true,
    val todayError: String? = null,
    val todayNetworkFailure: Boolean = false,
    val historyNetworkFailure: Boolean = false,
    val calendarNetworkFailure: Boolean = false,
    val historyLoaded: Boolean = false,
    val calendarLoaded: Boolean = false,
    val commonError: CollectionCommonError? = null,
    val appendError: String? = null,
    val isLoadingCards: Boolean = true,
    val cardsError: String? = null,
    val hasNext: Boolean = false,
    val isLoadingNext: Boolean = false,
    val isRefreshRequestActive: Boolean = false,
    val isRefreshIndicatorVisible: Boolean = false,
    val scrollToTopRequestId: Long = 0,
    val selectedCardId: String? = null,
    val selectedCard: CollectionCardUiModel? = null,
    val isLoadingCard: Boolean = false,
    val cardError: String? = null,
    val claimingState: EmotionType? = null,
    val claimError: String? = null,
    val claimOutcomeUnknown: Boolean = false,
    val calendarMonth: String = currentCollectionMonth(),
    val calendarCards: List<CollectionCardUiModel> = emptyList(),
    val isLoadingCalendar: Boolean = true,
    val calendarError: String? = null,
    val day: CollectionDayUiState = CollectionDayUiState(),
)

internal data class CollectionDayUiState(
    val selectedCalendarCardId: String? = null,
    val calendarEmotions: List<CollectionEmotion> = emptyList(),
    val calendarEmotionsLoaded: Boolean = false,
    val isLoadingCalendarEmotions: Boolean = false,
    val isLoadingNextCalendarEmotions: Boolean = false,
    val calendarEmotionsError: String? = null,
    val hasNextCalendarEmotions: Boolean = false,
    val emotionDeleteTargetId: Long? = null,
    val isDeletingEmotion: Boolean = false,
    val emotionDeleteFailed: Boolean = false,
)

/** Owns collection reads, the idempotent claim intent, and detail loading for this root tab. */
internal class CollectionViewModel(
    private val repository: CollectionRepository,
    private val deleteEmotion: suspend (Long) -> EmotionModerationResult = { EmotionModerationResult.Unavailable },
    internal val audioLoader: CollectionAudioLoader? = null,
) : ViewModel() {
    @Inject
    constructor(
        repository: CollectionRepository,
        deleteEmotion: DeleteEmotionUseCase,
        audioLoader: CollectionAudioLoader,
    ) : this(repository, deleteEmotion::invoke, audioLoader)

    private val mutableUiState = MutableStateFlow(CollectionUiState())
    val uiState = mutableUiState.asStateFlow()

    fun screenUiModel(
        state: CollectionUiState,
        notificationDate: String?,
        imageActionResult: CollectionImageActionResult?,
        imageActionResultId: Int,
        imageActionPhase: CollectionCardExportPhase,
    ): CollectionScreenUiModel =
        state.toScreenUiModel(notificationDate, imageActionResult, imageActionResultId, imageActionPhase)

    private var sessionGeneration: Long? = null
    private var todayJob: Job? = null
    private var historyJob: Job? = null
    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var calendarJob: Job? = null
    private var calendarEmotionsJob: Job? = null
    private var notificationRefreshPending = false
    private var refreshRequestId = 0L
    private var todayRequestId = 0L
    private var historyRequestId = 0L
    private var calendarRequestId = 0L
    private var calendarEmotionsRequestId = 0L
    private var calendarEmotionsCursor: String? = null
    private var pendingCalendarCardId: String? = null
    private var refreshedCalendarEmotions = emptyList<CollectionEmotion>()
    private val calendarEmotionsByCard = mutableMapOf<String, List<CollectionEmotion>>()
    private var historyCursor: String? = null
    private val historyByFilter = mutableMapOf<EmotionType?, HistorySnapshot>()
    private var pendingClaim: ClaimIntent? = null
    private var historyScrollResetPending = false

    fun onSessionChanged(
        generation: Long?,
        currentNickname: String?,
    ) {
        if (sessionGeneration == generation) {
            mutableUiState.update { state ->
                val title = currentNickname?.takeIf(String::isNotBlank) ?: "나"
                state.copy(
                    nickname = currentNickname,
                    cards = state.cards.map { it.copy(title = title) },
                    calendarCards = state.calendarCards.map { it.copy(title = title) },
                    selectedCard = state.selectedCard?.copy(title = title),
                )
            }
            return
        }
        sessionGeneration = generation
        notificationRefreshPending = false
        refreshRequestId += 1
        todayRequestId += 1
        historyRequestId += 1
        calendarRequestId += 1
        calendarEmotionsRequestId += 1
        refreshJob?.cancel()
        todayJob?.cancel()
        historyJob?.cancel()
        detailJob?.cancel()
        calendarJob?.cancel()
        calendarEmotionsJob?.cancel()
        refreshedCalendarEmotions = emptyList()
        calendarEmotionsCursor = null
        historyCursor = null
        historyByFilter.clear()
        calendarEmotionsByCard.clear()
        pendingCalendarCardId = null
        pendingClaim = null
        historyScrollResetPending = false
        mutableUiState.value = CollectionUiState(nickname = currentNickname)
        if (generation != null) refresh()
    }

    fun onScreenResumed() = refresh(resetToTop = false, showIndicator = true)

    fun refreshToday() {
        if (refreshJob?.isActive == true || mutableUiState.value.isRefreshRequestActive) return
        loadToday(showLoading = true)
    }

    private fun loadToday(showLoading: Boolean): Job? {
        val generation = sessionGeneration ?: return null
        val requestId = ++todayRequestId
        todayJob?.cancel()
        if (showLoading) {
            mutableUiState.update { it.copy(isLoadingToday = true, todayError = null) }
        }
        val job =
            viewModelScope.launch {
                when (val result = safeRepositoryCall { repository.findToday() }) {
                    is CollectionResult.Success -> {
                        if (sessionGeneration == generation && todayRequestId == requestId) {
                            mutableUiState.update {
                                it.copy(
                                    today = result.value,
                                    isLoadingToday = false,
                                    todayError = null,
                                    todayNetworkFailure = false,
                                )
                            }
                        }
                    }

                    is CollectionResult.Failure -> {
                        if (sessionGeneration == generation && todayRequestId == requestId) {
                            mutableUiState.update {
                                it.copy(
                                    todayError = messageFor(result.reason),
                                    isLoadingToday = false,
                                    todayNetworkFailure =
                                        result.reason.code == "NETWORK",
                                )
                            }
                        }
                    }
                }
            }
        todayJob = job
        return job
    }

    fun retryCards() {
        val state = mutableUiState.value
        if (refreshJob?.isActive == true || state.isLoadingCards) return
        historyJob?.cancel()
        loadFirstHistoryPage(state.selectedFilter)
    }

    fun refresh() = refresh(resetToTop = true, showIndicator = true)

    fun refreshFromNotification() {
        if (refreshJob?.isActive == true) {
            notificationRefreshPending = true
            return
        }
        refresh(resetToTop = false, showIndicator = false)
    }

    private fun refresh(
        resetToTop: Boolean,
        showIndicator: Boolean,
        cancelInFlight: Boolean = false,
    ) {
        val generation = sessionGeneration ?: return
        val shouldResetToTop = resetToTop && !historyScrollResetPending
        if (refreshJob?.isActive == true) {
            if (!cancelInFlight) {
                if (shouldResetToTop) {
                    mutableUiState.update { it.copy(scrollToTopRequestId = it.scrollToTopRequestId + 1) }
                }
                return
            }
            refreshRequestId += 1
            todayRequestId += 1
            historyRequestId += 1
            refreshJob?.cancel()
            todayJob?.cancel()
            historyJob?.cancel()
            refreshJob = null
        }
        val requestId = ++refreshRequestId
        todayJob?.cancel()
        historyJob?.cancel()
        val state = mutableUiState.value
        val filter = state.selectedFilter
        mutableUiState.update {
            it.copy(
                isLoadingToday = it.today == null,
                todayError = null,
                todayNetworkFailure = false,
                historyNetworkFailure = false,
                commonError = null,
                appendError = null,
                isLoadingCards = it.cards.isEmpty(),
                cardsError = null,
                isLoadingNext = false,
                isRefreshRequestActive = true,
                isRefreshIndicatorVisible = false,
                scrollToTopRequestId =
                    if (shouldResetToTop) it.scrollToTopRequestId + 1 else it.scrollToTopRequestId,
            )
        }
        val todayRequest = loadToday(showLoading = state.today == null)
        val historyRequest = loadFirstHistoryPage(filter)
        val calendarRequest = loadCalendar(state.calendarMonth)
        val batchCalendarRequestId = calendarRequestId
        val batchTodayRequestId = todayRequestId
        val batchHistoryRequestId = historyRequestId
        refreshJob =
            viewModelScope.launch {
                try {
                    val waitForRequests: suspend () -> Unit = {
                        listOfNotNull(todayRequest, historyRequest, calendarRequest).joinAll()
                    }
                    if (showIndicator) {
                        withDelayedLoadingIndicator(
                            onVisibleChanged = { visible ->
                                if (sessionGeneration == generation && refreshRequestId == requestId) {
                                    mutableUiState.update { it.copy(isRefreshIndicatorVisible = visible) }
                                }
                            },
                            block = waitForRequests,
                        )
                    } else {
                        waitForRequests()
                    }
                } finally {
                    if (sessionGeneration == generation && refreshRequestId == requestId) {
                        mutableUiState.update {
                            it.copy(
                                commonError =
                                    if (calendarRequestId == batchCalendarRequestId &&
                                        todayRequestId == batchTodayRequestId &&
                                        historyRequestId == batchHistoryRequestId &&
                                        it.todayNetworkFailure && it.historyNetworkFailure && it.calendarNetworkFailure
                                    ) {
                                        if (it.today != null || it.historyLoaded || it.calendarLoaded) {
                                            CollectionCommonError.Refresh
                                        } else {
                                            CollectionCommonError.Initial
                                        }
                                    } else {
                                        null
                                    },
                                isRefreshRequestActive = false,
                                isRefreshIndicatorVisible = false,
                            )
                        }
                        refreshJob = null
                        if (notificationRefreshPending) {
                            notificationRefreshPending = false
                            refresh(resetToTop = false, showIndicator = false)
                        }
                    }
                }
            }
    }

    fun setFilter(filter: EmotionType?) {
        if (sessionGeneration == null) return
        if (mutableUiState.value.selectedFilter == filter) return
        refreshRequestId += 1
        refreshJob?.cancel()
        refreshJob = null
        historyJob?.cancel()
        val cached = historyByFilter[filter]
        historyCursor = cached?.cursor
        mutableUiState.update {
            it.copy(
                selectedFilter = filter,
                cards = cached?.cards.orEmpty().map { card -> card.copy(title = it.nickname ?: card.title) },
                totalCount = cached?.totalCount ?: it.totalCount,
                emotionCounts = cached?.emotionCounts ?: it.emotionCounts,
                historyLoaded = cached != null,
                isLoadingCards = cached == null,
                cardsError = null,
                hasNext = cached?.hasNext ?: false,
                isLoadingNext = false,
                isRefreshRequestActive = cached == null,
                isRefreshIndicatorVisible = false,
                selectedCardId = null,
                selectedCard = null,
                cardError = null,
            )
        }
        loadFirstHistoryPage(filter, showIndicator = cached == null)
        if (notificationRefreshPending) {
            notificationRefreshPending = false
            refresh(resetToTop = false, showIndicator = false, cancelInFlight = true)
        }
    }

    fun loadNextPage() {
        val generation = sessionGeneration ?: return
        val state = mutableUiState.value
        val cursor = historyCursor ?: return
        if (!state.hasNext || state.isLoadingNext || state.isLoadingCards || state.isRefreshRequestActive) return
        val requestId = ++historyRequestId
        historyJob?.cancel()
        mutableUiState.update { it.copy(isLoadingNext = true, appendError = null) }
        historyJob =
            viewModelScope.launch {
                val result = loadHistoryPage(state.selectedFilter, cursor)
                if (sessionGeneration == generation && historyRequestId == requestId &&
                    mutableUiState.value.selectedFilter == state.selectedFilter
                ) {
                    applyHistoryResult(result, append = true)
                }
            }
    }

    fun setCalendarMonth(month: String) {
        if (month == mutableUiState.value.calendarMonth) return
        pendingCalendarCardId = null
        refreshedCalendarEmotions = emptyList()
        calendarEmotionsRequestId += 1
        calendarEmotionsJob?.cancel()
        calendarEmotionsCursor = null
        mutableUiState.update {
            it.copy(
                calendarMonth = month,
                calendarCards = emptyList(),
                calendarLoaded = false,
                isLoadingCalendar = true,
                calendarError = null,
                day = CollectionDayUiState(isDeletingEmotion = it.day.isDeletingEmotion),
            )
        }
        loadCalendar(month)
    }

    fun retryCalendar() {
        loadCalendar(mutableUiState.value.calendarMonth)
    }

    fun openCalendarDay(
        month: String,
        cardId: String,
    ) {
        setCalendarMonth(month)
        pendingCalendarCardId = cardId
        applyPendingCalendarCard()
    }

    private fun applyPendingCalendarCard() {
        val cardId = pendingCalendarCardId ?: return
        val state = mutableUiState.value
        if (!state.calendarLoaded) return
        pendingCalendarCardId = null
        selectCalendarCard(cardId)
    }

    fun selectCalendarCard(cardId: String) {
        val state = mutableUiState.value
        if (state.calendarCards.none { it.id == cardId } || state.day.selectedCalendarCardId == cardId) return
        val cachedEmotions = calendarEmotionsByCard[cardId]
        calendarEmotionsRequestId += 1
        calendarEmotionsJob?.cancel()
        calendarEmotionsCursor = null
        mutableUiState.update {
            it.copy(
                day =
                    it.day.copy(
                        selectedCalendarCardId = cardId,
                        emotionDeleteTargetId = null,
                        emotionDeleteFailed = false,
                        calendarEmotions = cachedEmotions.orEmpty(),
                        calendarEmotionsLoaded = cachedEmotions != null,
                        isLoadingCalendarEmotions = false,
                        isLoadingNextCalendarEmotions = false,
                        calendarEmotionsError = null,
                        hasNextCalendarEmotions = false,
                    ),
            )
        }
        loadCalendarEmotions(cardId, cursor = null, append = false)
    }

    fun closeCalendarDay() {
        pendingCalendarCardId = null
        refreshedCalendarEmotions = emptyList()
        calendarEmotionsRequestId += 1
        calendarEmotionsJob?.cancel()
        calendarEmotionsJob = null
        calendarEmotionsCursor = null
        mutableUiState.update {
            it.copy(
                day = CollectionDayUiState(isDeletingEmotion = it.day.isDeletingEmotion),
            )
        }
    }

    fun loadNextCalendarEmotions() {
        val state = mutableUiState.value
        val cardId = state.day.selectedCalendarCardId ?: return
        val cursor = calendarEmotionsCursor ?: return
        if (!state.day.hasNextCalendarEmotions || state.day.isLoadingCalendarEmotions ||
            state.day.isLoadingNextCalendarEmotions
        ) {
            return
        }
        loadCalendarEmotions(cardId, cursor, append = true)
    }

    fun requestEmotionDeletion(emotionId: Long) {
        val state = mutableUiState.value
        if (state.day.isDeletingEmotion || state.day.calendarEmotions.none { it.id == emotionId }) return
        mutableUiState.update {
            it.copy(
                day = it.day.copy(emotionDeleteTargetId = emotionId, emotionDeleteFailed = false),
            )
        }
    }

    fun cancelEmotionDeletion() {
        if (mutableUiState.value.day.isDeletingEmotion) return
        mutableUiState.update { it.copy(day = it.day.copy(emotionDeleteTargetId = null, emotionDeleteFailed = false)) }
    }

    fun confirmEmotionDeletion() {
        val generation = sessionGeneration ?: return
        val state = mutableUiState.value
        val emotionId = state.day.emotionDeleteTargetId ?: return
        if (state.day.isDeletingEmotion) return
        mutableUiState.update { it.copy(day = it.day.copy(isDeletingEmotion = true, emotionDeleteFailed = false)) }
        viewModelScope.launch {
            val result =
                try {
                    deleteEmotion(emotionId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    EmotionModerationResult.Unavailable
                }
            if (sessionGeneration != generation) return@launch
            val deleted = result == EmotionModerationResult.Success || result == EmotionModerationResult.NotFound
            mutableUiState.update {
                it.copy(
                    day =
                        it.day.copy(
                            isDeletingEmotion = false,
                            emotionDeleteTargetId = it.day.emotionDeleteTargetId.takeUnless { deleted },
                            emotionDeleteFailed = !deleted && it.day.emotionDeleteTargetId == emotionId,
                        ),
                )
            }
            if (deleted) {
                state.day.selectedCalendarCardId?.let { cardId ->
                    calendarEmotionsByCard[cardId]?.let { cached ->
                        calendarEmotionsByCard[cardId] = cached.filterNot { it.id == emotionId }
                    }
                }
                retryCalendar()
                val cardId = mutableUiState.value.day.selectedCalendarCardId
                if (cardId == state.day.selectedCalendarCardId && cardId != null) {
                    mutableUiState.update { current ->
                        current.copy(
                            day =
                                current.day.copy(
                                    calendarEmotions =
                                        current.day.calendarEmotions.filterNot {
                                            it.id ==
                                                emotionId
                                        },
                                ),
                        )
                    }
                    loadCalendarEmotions(cardId, cursor = null, append = false)
                }
            }
        }
    }

    fun retryCalendarEmotions() {
        val state = mutableUiState.value
        val cardId = state.day.selectedCalendarCardId ?: return
        if (calendarEmotionsJob?.isActive == true) return
        loadCalendarEmotions(cardId, cursor = null, append = false)
    }

    fun retryNextCalendarEmotions() {
        if (calendarEmotionsCursor != null) loadNextCalendarEmotions() else retryCalendarEmotions()
    }

    private fun loadCalendar(month: String): Job? {
        val generation = sessionGeneration ?: return null
        val requestId = ++calendarRequestId
        calendarJob?.cancel()
        mutableUiState.update {
            it.copy(isLoadingCalendar = true, calendarError = null, calendarNetworkFailure = false, commonError = null)
        }
        val job =
            viewModelScope.launch {
                when (val result = safeRepositoryCall { repository.findCalendar(month) }) {
                    is CollectionResult.Success -> {
                        if (sessionGeneration == generation && calendarRequestId == requestId &&
                            mutableUiState.value.calendarMonth == month
                        ) {
                            val cards = result.value.cards.map { it.toUiModel(mutableUiState.value.nickname) }
                            mutableUiState.update { state ->
                                val selectedCardStillExists =
                                    state.day.selectedCalendarCardId == null ||
                                        cards.any { it.id == state.day.selectedCalendarCardId }
                                state.copy(
                                    calendarCards = cards,
                                    calendarLoaded = true,
                                    isLoadingCalendar = false,
                                    calendarError = null,
                                    day =
                                        state.day.copy(
                                            selectedCalendarCardId =
                                                state.day.selectedCalendarCardId.takeIf {
                                                    selectedCardStillExists
                                                },
                                        ),
                                )
                            }
                            if (mutableUiState.value.day.selectedCalendarCardId == null &&
                                pendingCalendarCardId == null
                            ) {
                                closeCalendarDay()
                            }
                            applyPendingCalendarCard()
                        }
                    }

                    is CollectionResult.Failure -> {
                        if (sessionGeneration == generation && calendarRequestId == requestId) {
                            mutableUiState.update {
                                it.copy(
                                    isLoadingCalendar = false,
                                    calendarError = messageFor(result.reason),
                                    calendarNetworkFailure =
                                        result.reason.code == "NETWORK",
                                )
                            }
                        }
                    }
                }
            }
        calendarJob = job
        return job
    }

    private fun loadCalendarEmotions(
        cardId: String,
        cursor: String?,
        append: Boolean,
    ) {
        val generation = sessionGeneration ?: return
        val requestId = ++calendarEmotionsRequestId
        calendarEmotionsJob?.cancel()
        val isSilentRefresh = !append && calendarEmotionsByCard.containsKey(cardId)
        if (!append) {
            calendarEmotionsCursor = null
            refreshedCalendarEmotions = emptyList()
        }
        mutableUiState.update {
            it.copy(
                day =
                    it.day.copy(
                        isLoadingCalendarEmotions = false,
                        isLoadingNextCalendarEmotions = append,
                        calendarEmotionsError = null,
                        hasNextCalendarEmotions = if (append) it.day.hasNextCalendarEmotions else false,
                    ),
            )
        }
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val request: suspend () -> CollectionResult<CollectionEmotionPage> = {
                    safeRepositoryCall { repository.findEmotions(cardId, cursor) }
                }
                val result =
                    if (!append && !isSilentRefresh) {
                        withDelayedLoadingIndicator(
                            onVisibleChanged = { visible ->
                                if (visible && sessionGeneration == generation &&
                                    calendarEmotionsRequestId == requestId &&
                                    mutableUiState.value.day.selectedCalendarCardId == cardId
                                ) {
                                    mutableUiState.update {
                                        it.copy(
                                            day = it.day.copy(isLoadingCalendarEmotions = true),
                                        )
                                    }
                                }
                            },
                            block = request,
                        )
                    } else {
                        request()
                    }
                when (result) {
                    is CollectionResult.Success -> {
                        if (sessionGeneration == generation && calendarEmotionsRequestId == requestId &&
                            mutableUiState.value.day.selectedCalendarCardId == cardId
                        ) {
                            applyCalendarEmotions(cardId, result.value)
                        }
                    }

                    is CollectionResult.Failure -> {
                        if (sessionGeneration == generation && calendarEmotionsRequestId == requestId &&
                            mutableUiState.value.day.selectedCalendarCardId == cardId
                        ) {
                            if (append && result.reason.code == "COLLECTION-007") {
                                loadCalendarEmotions(cardId, cursor = null, append = false)
                                return@launch
                            }
                            mutableUiState.update {
                                it.copy(
                                    day =
                                        it.day.copy(
                                            isLoadingCalendarEmotions = false,
                                            isLoadingNextCalendarEmotions = false,
                                            calendarEmotionsError = messageFor(result.reason),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        calendarEmotionsJob = job
        job.start()
    }

    private fun applyCalendarEmotions(
        cardId: String,
        page: CollectionEmotionPage,
    ) {
        calendarEmotionsCursor = page.nextCursor
        refreshedCalendarEmotions = (refreshedCalendarEmotions + page.items).distinctBy { it.id }
        // Keep the cached tail visible until pagination reaches the end of the fresh list.
        val emotions =
            if (page.hasNext) {
                (refreshedCalendarEmotions + calendarEmotionsByCard[cardId].orEmpty()).distinctBy { it.id }
            } else {
                refreshedCalendarEmotions
            }
        calendarEmotionsByCard[cardId] = emotions
        mutableUiState.update { state ->
            state.copy(
                day =
                    state.day.copy(
                        calendarEmotions = emotions,
                        calendarEmotionsLoaded = true,
                        isLoadingCalendarEmotions = false,
                        isLoadingNextCalendarEmotions = false,
                        calendarEmotionsError = null,
                        hasNextCalendarEmotions = page.hasNext,
                    ),
            )
        }
    }

    fun selectCard(cardId: String) {
        val generation = sessionGeneration ?: return
        detailJob?.cancel()
        val cached = mutableUiState.value.cards.firstOrNull { it.id == cardId }
        mutableUiState.update {
            it.copy(
                selectedCardId = cardId,
                selectedCard = cached,
                isLoadingCard = true,
                cardError = null,
            )
        }
        detailJob =
            viewModelScope.launch {
                when (val result = safeRepositoryCall { repository.findCard(cardId) }) {
                    is CollectionResult.Success -> {
                        if (sessionGeneration == generation && mutableUiState.value.selectedCardId == cardId) {
                            mutableUiState.update { state ->
                                state.copy(selectedCard = result.value.toUiModel(state.nickname), isLoadingCard = false)
                            }
                        }
                    }

                    is CollectionResult.Failure -> {
                        if (sessionGeneration == generation &&
                            mutableUiState.value.selectedCardId == cardId
                        ) {
                            mutableUiState.update {
                                it.copy(
                                    cardError = messageFor(result.reason),
                                    isLoadingCard = false,
                                )
                            }
                        }
                    }
                }
            }
    }

    fun dismissCard() {
        detailJob?.cancel()
        mutableUiState.update {
            it.copy(
                selectedCardId = null,
                selectedCard = null,
                isLoadingCard = false,
                cardError = null,
            )
        }
    }

    fun retrySelectedCard() {
        mutableUiState.value.selectedCardId?.let(::selectCard)
    }

    fun claim(state: EmotionType) {
        if (sessionGeneration == null) return
        if (mutableUiState.value.claimingState != null) return
        val today = mutableUiState.value.today ?: return
        val item = today.items.firstOrNull { it.state == state.toDomain() } ?: return
        if (!today.canClaim || today.claimEmotion != item.state || item.isClaimed) return
        pendingClaim = ClaimIntent(today.date, state.toDomain())
        sendClaim()
    }

    fun retryClaim() {
        if (mutableUiState.value.claimingState != null) return
        if (pendingClaim != null) sendClaim() else refreshToday()
    }

    private fun sendClaim() {
        val generation = sessionGeneration ?: return
        val intent = pendingClaim ?: return
        mutableUiState.update {
            it.copy(claimingState = intent.state.toUi(), claimError = null, claimOutcomeUnknown = false)
        }
        viewModelScope.launch {
            when (val result = safeRepositoryCall { repository.claim(intent.date, intent.state) }) {
                is CollectionResult.Success -> {
                    if (sessionGeneration != generation || pendingClaim != intent) return@launch
                    pendingClaim = null
                    mutableUiState.update { state ->
                        state.copy(
                            today =
                                state.today?.let { today ->
                                    today.copy(
                                        items =
                                            today.items.map { item ->
                                                if (item.state == intent.state) item.copy(card = result.value) else item
                                            },
                                    )
                                },
                            claimingState = null,
                            claimError = null,
                            claimOutcomeUnknown = false,
                        )
                    }
                    historyScrollResetPending = true
                    refresh(
                        resetToTop = false,
                        showIndicator = false,
                        cancelInFlight = true,
                    )
                }

                is CollectionResult.Failure -> {
                    if (sessionGeneration != generation || pendingClaim != intent) return@launch
                    val unknown = result.reason.isOutcomeUnknown
                    if (!unknown) pendingClaim = null
                    mutableUiState.update {
                        it.copy(
                            claimingState = null,
                            claimError = messageFor(result.reason),
                            claimOutcomeUnknown = unknown,
                        )
                    }
                    refreshTodayOnly()
                }
            }
        }
    }

    private fun refreshTodayOnly() {
        loadToday(showLoading = false)
    }

    private fun loadFirstHistoryPage(
        filter: EmotionType?,
        showIndicator: Boolean = false,
    ): Job? {
        val generation = sessionGeneration ?: return null
        val requestId = ++historyRequestId
        mutableUiState.update {
            it.copy(
                isLoadingCards = true,
                cardsError = null,
                historyNetworkFailure = false,
                appendError = null,
                commonError = null,
            )
        }
        val job =
            viewModelScope.launch {
                val request: suspend () -> Unit = {
                    val result = loadHistoryPage(filter, cursor = null)
                    if (sessionGeneration == generation && historyRequestId == requestId &&
                        mutableUiState.value.selectedFilter == filter
                    ) {
                        applyHistoryResult(result, append = false)
                    }
                }
                try {
                    if (showIndicator) {
                        withDelayedLoadingIndicator(
                            onVisibleChanged = { visible ->
                                if (sessionGeneration == generation &&
                                    historyRequestId == requestId &&
                                    mutableUiState.value.selectedFilter == filter
                                ) {
                                    mutableUiState.update { it.copy(isRefreshIndicatorVisible = visible) }
                                }
                            },
                            block = request,
                        )
                    } else {
                        request()
                    }
                } finally {
                    if (showIndicator && sessionGeneration == generation &&
                        historyRequestId == requestId && mutableUiState.value.selectedFilter == filter
                    ) {
                        mutableUiState.update {
                            it.copy(
                                isRefreshRequestActive = false,
                                isRefreshIndicatorVisible = false,
                            )
                        }
                    }
                }
            }
        historyJob = job
        return job
    }

    private suspend fun loadHistoryPage(
        filter: EmotionType?,
        cursor: String?,
    ): CollectionResult<com.pheeeew.domain.model.collection.CollectionHistory> =
        safeRepositoryCall {
            repository.findHistory(
                CollectionHistoryQuery(
                    emotion = filter?.toDomain(),
                    cursor = cursor,
                ),
            )
        }

    private fun applyHistoryResult(
        result: CollectionResult<com.pheeeew.domain.model.collection.CollectionHistory>,
        append: Boolean,
    ) {
        when (result) {
            is CollectionResult.Success -> {
                historyCursor = result.value.nextCursor
                val resetScroll = !append && historyScrollResetPending
                mutableUiState.update { state ->
                    val cards = result.value.cards.map { it.toUiModel(result.value.nickname ?: state.nickname) }
                    val updatedCards =
                        if (append) (state.cards + cards).distinctBy(CollectionCardUiModel::id) else cards
                    val selectionRemoved =
                        state.selectedCardId != null && updatedCards.none { it.id == state.selectedCardId }
                    state.copy(
                        nickname = result.value.nickname ?: state.nickname,
                        cards = updatedCards,
                        historyLoaded = true,
                        scrollToTopRequestId =
                            if (resetScroll) state.scrollToTopRequestId + 1 else state.scrollToTopRequestId,
                        totalCount = result.value.counts.total,
                        emotionCounts =
                            result.value.counts.byEmotion
                                .mapKeys { it.key.toUi() },
                        hasNext = result.value.hasNext,
                        isLoadingCards = false,
                        isLoadingNext = false,
                        cardsError = null,
                        selectedCardId = if (selectionRemoved) null else state.selectedCardId,
                        selectedCard = if (selectionRemoved) null else state.selectedCard,
                        isLoadingCard = if (selectionRemoved) false else state.isLoadingCard,
                        cardError = if (selectionRemoved) null else state.cardError,
                    )
                }
                if (resetScroll) historyScrollResetPending = false
                val updated = mutableUiState.value
                historyByFilter[updated.selectedFilter] =
                    HistorySnapshot(
                        cards = updated.cards,
                        totalCount = updated.totalCount,
                        emotionCounts = updated.emotionCounts,
                        hasNext = updated.hasNext,
                        cursor = historyCursor,
                    )
            }

            is CollectionResult.Failure -> {
                mutableUiState.update {
                    it.copy(
                        isLoadingCards = false,
                        isLoadingNext = false,
                        cardsError = if (append) it.cardsError else messageFor(result.reason),
                        appendError = if (append) messageFor(result.reason) else null,
                        historyNetworkFailure =
                            if (append) {
                                it.historyNetworkFailure
                            } else {
                                result.reason.code ==
                                    "NETWORK"
                            },
                    )
                }
            }
        }
    }

    private fun CollectionCard.toUiModel(nickname: String?): CollectionCardUiModel =
        CollectionCardUiModel(
            id = reference.cardId,
            period = formatCollectionCardDate(reference.date),
            title = nickname?.takeIf(String::isNotBlank) ?: "나",
            emotion = reference.state.toUi(),
            pressLabel = "프레스",
            pressCount = pressCount,
            stampLabel = "스탬프",
            stampCount = stampCount,
            characterImageUrl = reference.images.characterUrl,
            textStampImageUrl = reference.images.straightStampUrl,
            roundStampImageUrl = reference.images.roundStampUrl,
        )

    private suspend fun <T> safeRepositoryCall(block: suspend () -> CollectionResult<T>): CollectionResult<T> =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CollectionResult.Failure(CollectionFailure(null, "UNEXPECTED", null))
        }

    private fun messageFor(failure: CollectionFailure): String =
        when (failure.code) {
            "COLLECTION-002" -> {
                "오늘 날짜가 지나 카드를 받을 수 없어요. " +
                    "오늘의 진행도를 새로고침해 주세요."
            }

            "COLLECTION-003" -> {
                "아직 카드 받기 조건을 채우지 못했어요. " +
                    "진행도를 새로고침해 주세요."
            }

            "COLLECTION-005" -> {
                "이 카드를 찾을 수 없어요. 목록을 새로고침해 주세요."
            }

            "COLLECTION-007", "COLLECTION-008" -> {
                "목록이 변경되어 다시 불러와야 해요."
            }

            "COLLECTION-006", "COLLECTION-004" -> {
                "카드 정보를 불러오지 못했어요. " +
                    "잠시 후 다시 시도해 주세요."
            }

            "COLLECTION-CONTRACT", "CONTRACT" -> {
                "서버 응답을 확인할 수 없어요. " +
                    "잠시 후 다시 시도해 주세요."
            }

            else -> {
                "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
            }
        }

    private data class ClaimIntent(
        val date: String,
        val state: EmotionState,
    )

    private data class HistorySnapshot(
        val cards: List<CollectionCardUiModel>,
        val totalCount: Long,
        val emotionCounts: Map<EmotionType, Long>,
        val hasNext: Boolean,
        val cursor: String?,
    )
}

private fun EmotionType.toDomain(): EmotionState = EmotionState.valueOf(name)

private fun EmotionState.toUi(): EmotionType = EmotionType.valueOf(name)

private fun CollectionUiState.toScreenUiModel(
    notificationDate: String?,
    imageActionResult: CollectionImageActionResult?,
    imageActionResultId: Int,
    imageActionPhase: CollectionCardExportPhase,
): CollectionScreenUiModel =
    CollectionScreenUiModel(
        isNotificationDateOutdated = notificationDate != null && today != null && notificationDate != today.date,
        nickname = nickname?.takeIf(String::isNotBlank) ?: "나",
        cards = cards,
        totalCount = totalCount,
        emotionCounts = emotionCounts,
        selectedFilter = selectedFilter,
        hasClaimedToday = today?.hasClaimedCard == true,
        todayCardId =
            today
                ?.items
                ?.firstOrNull { it.card != null }
                ?.card
                ?.cardId,
        pendingProgress =
            today
                ?.takeUnless { it.hasClaimedCard }
                ?.let { progress ->
                    CollectionProgressUiModel(
                        emotion = progress.claimEmotion?.toUi() ?: collectionCoverFlowEmotionOrder.first(),
                        stampCount = progress.totalStampCount,
                        stampTarget = COLLECTION_STAMP_CLAIM_TARGET,
                        stampRemaining = (COLLECTION_STAMP_CLAIM_TARGET - progress.totalStampCount).coerceAtLeast(0),
                        stampProgressByEmotion =
                            collectionCoverFlowEmotionOrder.mapNotNull { emotion ->
                                progress.items
                                    .firstOrNull { it.state.name == emotion.name }
                                    ?.let { item -> CollectionEmotionProgress(emotion, item.stamps.count) }
                            },
                        pressCount = progress.totalPressCount,
                        pressTarget = COLLECTION_PRESS_CLAIM_TARGET,
                        pressRemaining = (COLLECTION_PRESS_CLAIM_TARGET - progress.totalPressCount).coerceAtLeast(0),
                        canClaim = progress.canClaim,
                        pressProgressByEmotion =
                            collectionCoverFlowEmotionOrder.mapNotNull { emotion ->
                                progress.items
                                    .firstOrNull { it.state.name == emotion.name }
                                    ?.let { item -> CollectionEmotionProgress(emotion, item.presses.count) }
                            },
                        hasProgress = true,
                        isClaiming = claimingState != null,
                    )
                },
        commonError = commonError,
        appendError = appendError,
        historyLoaded = historyLoaded,
        calendarLoaded = calendarLoaded,
        todayLoaded = today != null,
        deferSectionErrors = isRefreshRequestActive,
        todayLoading = isLoadingToday,
        todayError = todayError,
        isLoadingCards = isLoadingCards,
        isRefreshRequestActive = isRefreshRequestActive,
        isRefreshIndicatorVisible = isRefreshIndicatorVisible,
        scrollToTopRequestId = scrollToTopRequestId,
        cardsError = cardsError,
        isLoadingNext = isLoadingNext,
        hasNext = hasNext,
        claimError = claimError,
        claimOutcomeUnknown = claimOutcomeUnknown,
        selectedCard = selectedCard,
        isLoadingCard = isLoadingCard,
        cardError = cardError,
        saveCompletionCardId =
            imageActionResult
                ?.takeIf {
                    it.action == CollectionImageAction.SAVE && it.outcome == CollectionImageActionOutcome.SUCCEEDED
                }?.cardId,
        saveCompletionId = imageActionResultId,
        imageActionError =
            when {
                imageActionResult?.outcome == CollectionImageActionOutcome.CAPTURE_FAILED -> {
                    CollectionCardActionError.CAPTURE
                }

                imageActionResult?.outcome == CollectionImageActionOutcome.FAILED &&
                    imageActionResult.action == CollectionImageAction.SAVE -> {
                    CollectionCardActionError.SAVE
                }

                imageActionResult?.outcome == CollectionImageActionOutcome.FAILED -> {
                    CollectionCardActionError.SHARE
                }

                else -> {
                    null
                }
            },
        imageActionErrorCardId = imageActionResult?.cardId,
        imageActionErrorId = imageActionResultId,
        imageActionPhase = imageActionPhase,
        calendarMonth = calendarMonth,
        calendarCards = calendarCards,
        isLoadingCalendar = isLoadingCalendar,
        calendarError = calendarError,
    )
