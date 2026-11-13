package com.pheeeew.feature.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.time.millisUntilNextKoreaCalendarDay
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
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.CollectionRepository
import com.pheeeew.domain.repository.EmotionFailure
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.repository.EmotionRepository
import com.pheeeew.domain.repository.EmotionResult
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.calendar.collectionDayOfWeek
import com.pheeeew.feature.screens.collection.calendar.collectionMonthDays
import com.pheeeew.feature.screens.collection.calendar.currentCollectionTodayDate
import com.pheeeew.feature.screens.collection.calendar.detail.CollectionAudioLoader
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

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
    val isCalendarCardSelected: Boolean = false,
    val selectedCard: CollectionCardUiModel? = null,
    val isLoadingCard: Boolean = false,
    val cardError: String? = null,
    val claimingState: EmotionType? = null,
    val claimError: String? = null,
    val claimOutcomeUnknown: Boolean = false,
    val todayDate: String = currentCollectionTodayDate(),
    val calendarMonth: String = todayDate.take(7),
    val calendarCards: List<CollectionCardUiModel> = emptyList(),
    val isLoadingCalendar: Boolean = true,
    val calendarError: String? = null,
    val day: CollectionDayUiState = CollectionDayUiState(),
)

internal data class CollectionDayUiState(
    val selectedCalendarCardId: String? = null,
    val selectedCalendarDate: String? = null,
    val emotionSnapshot: CollectionDayEmotionSnapshot = CollectionDayEmotionSnapshot(),
    val retainEmptyEmotions: Boolean = false,
    val calendarEmotionsLoaded: Boolean = false,
    val isLoadingCalendarEmotions: Boolean = false,
    val isLoadingNextCalendarEmotions: Boolean = false,
    val calendarEmotionsError: String? = null,
    val hasNextCalendarEmotions: Boolean = false,
    val emotionDeleteTargetId: Long? = null,
    val isDeletingEmotion: Boolean = false,
    val emotionDeleteFailed: Boolean = false,
    val pendingReactionIds: Set<Long> = emptySet(),
    val calendarReactionError: String? = null,
) {
    val calendarEmotions get() = emotionSnapshot.emotions
    val calendarEmotionItems get() = emotionSnapshot.items

    val emotionCount: Int?
        get() =
            calendarEmotionItems.size.takeIf {
                it > 0 && calendarEmotionsLoaded && calendarEmotionsError == null && !hasNextCalendarEmotions
            }
}

internal data class CollectionDayEmotionSnapshot(
    val emotions: List<CollectionEmotion> = emptyList(),
) {
    val items = emotions.map { it.toUiModel() }
}

internal sealed interface CollectionDayEmotionContent {
    data object Waiting : CollectionDayEmotionContent

    data object Loading : CollectionDayEmotionContent

    data object Empty : CollectionDayEmotionContent

    data class Error(
        val message: String,
    ) : CollectionDayEmotionContent

    data class Items(
        val items: List<CollectionDayEmotionUiModel>,
        val loadingMore: Boolean = false,
        val appendError: String? = null,
        val hasNext: Boolean = false,
    ) : CollectionDayEmotionContent {
        val canLoadMore: Boolean get() = hasNext && !loadingMore && appendError == null
    }
}

internal data class CollectionDayPageUiModel(
    val date: String,
    val title: String,
    val subtitle: String,
    val card: CollectionCardUiModel? = null,
)

private data class CollectionDayDetailInput(
    val todayDate: String,
    val matchesMonth: Boolean,
    val calendarCards: List<CollectionCardUiModel>,
    val day: CollectionDayUiState,
    val calendarError: String?,
    val commonError: CollectionCommonError?,
    val todayError: String?,
    val today: CollectionToday?,
    val claimingState: EmotionType?,
    val isLoadingToday: Boolean,
    val claimError: String?,
    val isLoadingCalendar: Boolean,
    val nickname: String?,
    val selectedCard: CollectionCardUiModel?,
    val isLoadingCard: Boolean,
    val cardError: String?,
)

internal data class CollectionDayDetailUiState(
    val cards: List<CollectionCardUiModel> = emptyList(),
    val pages: List<CollectionDayPageUiModel> = emptyList(),
    val selectedDate: String? = null,
    val todayDate: String? = null,
    val todayProgress: CollectionProgressUiModel? = null,
    val isLoadingToday: Boolean = false,
    val claimError: String? = null,
    val cardLoadError: CollectionDayCardLoadError? = null,
    val errorHeader: String = "컬렉션",
    val isLoadingCalendar: Boolean = true,
    val nickname: String = "나",
    val emotionContent: CollectionDayEmotionContent = CollectionDayEmotionContent.Waiting,
    val emotionCount: Int? = null,
    val pendingReactionIds: Set<Long> = emptySet(),
    val reactionError: String? = null,
    val deleteTargetId: Long? = null,
    val deleteFailed: Boolean = false,
    val selectedCard: CollectionCardUiModel? = null,
    val isLoadingCard: Boolean = false,
    val cardError: String? = null,
)

internal enum class CollectionDayRetryTarget { All, Calendar, Today }

internal data class CollectionDayCardLoadError(
    val message: String,
    val retryTarget: CollectionDayRetryTarget,
)

/** Owns collection reads, the idempotent claim intent, and detail loading for this root tab. */
internal class CollectionViewModel(
    private val repository: CollectionRepository,
    private val deleteEmotion: suspend (Long, Long) -> EmotionModerationResult = { _, _ ->
        EmotionModerationResult.Unavailable
    },
    private val reactEmotion: suspend (Long, EmotionReactionType, Boolean, Long) -> EmotionResult<Unit> =
        { _, _, _, _ -> EmotionResult.Failure(EmotionFailure.UNAVAILABLE) },
    internal val audioLoader: CollectionAudioLoader? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    @Inject
    constructor(
        repository: CollectionRepository,
        deleteEmotion: DeleteEmotionUseCase,
        emotionRepository: EmotionRepository,
        audioLoader: CollectionAudioLoader,
    ) : this(repository, deleteEmotion::invoke, emotionRepository::react, audioLoader)

    private val mutableUiState =
        MutableStateFlow(CollectionUiState(todayDate = currentCollectionTodayDate(clock.now())))
    val uiState = mutableUiState.asStateFlow()

    fun screenUiModel(
        state: CollectionUiState,
        notificationDate: String?,
        imageActionResult: CollectionImageActionResult?,
        imageActionResultId: Int,
        imageActionPhase: CollectionCardExportPhase,
    ): CollectionScreenUiModel =
        state.toScreenUiModel(notificationDate, imageActionResult, imageActionResultId, imageActionPhase)

    fun calendarDetailUiState(
        month: String,
        requestedDay: String,
    ) = uiState
        .map { state ->
            val matchesMonth = state.calendarMonth == month
            CollectionDayDetailInput(
                todayDate = state.todayDate,
                matchesMonth = matchesMonth,
                calendarCards = state.calendarCards.takeIf { matchesMonth }.orEmpty(),
                day = state.day.takeIf { matchesMonth } ?: CollectionDayUiState(),
                calendarError = state.calendarError.takeIf { matchesMonth },
                commonError = state.commonError.takeIf { matchesMonth },
                todayError = state.todayError,
                today = state.today,
                claimingState = state.claimingState,
                isLoadingToday = state.isLoadingToday,
                claimError = state.claimError,
                isLoadingCalendar = !matchesMonth || state.isLoadingCalendar,
                nickname = state.nickname,
                selectedCard = state.selectedCard,
                isLoadingCard = state.isLoadingCard,
                cardError = state.cardError,
            )
        }.distinctUntilChanged()
        .map { state ->
            val today = state.todayDate
            val cardsByDate = state.calendarCards.associateBy { it.collectionDate }
            val pages =
                collectionMonthDays(month)
                    .asReversed()
                    .filter { it.isCurrentMonth }
                    .mapNotNull { it.date }
                    .filter { it <= today }
                    .map { date ->
                        CollectionDayPageUiModel(
                            date = date,
                            title = "${date.substring(5, 7)}월 ${date.substring(8, 10)}일",
                            subtitle = "${date.take(4)}년 · ${collectionDayOfWeek(date)}",
                            card = cardsByDate[date],
                        )
                    }
            val day = state.day
            val cardError =
                when {
                    !state.matchesMonth -> {
                        null
                    }

                    state.calendarError != null -> {
                        CollectionDayCardLoadError(
                            state.calendarError,
                            if (state.commonError !=
                                null
                            ) {
                                CollectionDayRetryTarget.All
                            } else {
                                CollectionDayRetryTarget.Calendar
                            },
                        )
                    }

                    day.selectedCalendarDate == today -> {
                        state.todayError?.let {
                            CollectionDayCardLoadError(it, CollectionDayRetryTarget.Today)
                        }
                    }

                    else -> {
                        null
                    }
                }
            val emotionContent =
                when {
                    day.calendarEmotionItems.isNotEmpty() -> {
                        CollectionDayEmotionContent.Items(
                            day.calendarEmotionItems,
                            day.isLoadingNextCalendarEmotions,
                            day.calendarEmotionsError,
                            day.hasNextCalendarEmotions,
                        )
                    }

                    day.calendarEmotionsError != null -> {
                        CollectionDayEmotionContent.Error(day.calendarEmotionsError)
                    }

                    day.isLoadingCalendarEmotions -> {
                        CollectionDayEmotionContent.Loading
                    }

                    day.retainEmptyEmotions -> {
                        CollectionDayEmotionContent.Empty
                    }

                    day.calendarEmotionsLoaded -> {
                        CollectionDayEmotionContent.Empty
                    }

                    else -> {
                        CollectionDayEmotionContent.Waiting
                    }
                }
            CollectionDayDetailUiState(
                cards = state.calendarCards,
                pages = pages,
                selectedDate = day.selectedCalendarDate,
                todayDate = today,
                todayProgress = state.today?.takeIf { it.date == today }.toTodayProgress(state.claimingState),
                isLoadingToday = state.isLoadingToday,
                claimError = state.claimError,
                cardLoadError = cardError,
                errorHeader = pages.firstOrNull { it.date == requestedDay }?.title ?: "컬렉션",
                isLoadingCalendar = state.isLoadingCalendar,
                nickname = state.nickname ?: "나",
                emotionContent = emotionContent,
                emotionCount = day.emotionCount,
                pendingReactionIds = day.pendingReactionIds,
                reactionError = day.calendarReactionError,
                deleteTargetId = day.emotionDeleteTargetId,
                deleteFailed = day.emotionDeleteFailed,
                selectedCard = state.selectedCard,
                isLoadingCard = state.isLoadingCard,
                cardError = state.cardError,
            )
        }.distinctUntilChanged()

    fun retryCalendarDetailCards(target: CollectionDayRetryTarget) {
        when (target) {
            CollectionDayRetryTarget.All -> refresh()
            CollectionDayRetryTarget.Calendar -> retryCalendar()
            CollectionDayRetryTarget.Today -> refreshToday()
        }
    }

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
    private var detailRequestId = 0L
    private var calendarRequestId = 0L
    private var calendarEmotionsRequestId = 0L
    private var calendarEmotionsCursor: String? = null
    private var pendingCalendarCardId: String? = null
    private var refreshedCalendarEmotions = emptyList<CollectionEmotion>()
    private val calendarEmotionsByDate = mutableMapOf<String, List<CollectionEmotion>>()
    private val pendingReactions = mutableMapOf<Long, String>()
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
        detailRequestId += 1
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
        calendarEmotionsByDate.clear()
        pendingReactions.clear()
        pendingCalendarCardId = null
        pendingClaim = null
        historyScrollResetPending = false
        mutableUiState.value =
            CollectionUiState(nickname = currentNickname, todayDate = currentCollectionTodayDate(clock.now()))
        if (generation != null) refresh()
    }

    fun onScreenResumed() {
        val dateChanged = updateTodayDate()
        refresh(resetToTop = false, showIndicator = true, cancelInFlight = dateChanged)
    }

    fun onCalendarDetailResumed() {
        if (updateTodayDate()) {
            loadToday(showLoading = true)
        } else {
            refreshToday()
        }
    }

    suspend fun observeCalendarDate() {
        while (true) {
            delay(millisUntilNextKoreaCalendarDay(clock.now().toEpochMilliseconds()))
            if (updateTodayDate()) loadToday(showLoading = true)
        }
    }

    private fun updateTodayDate(): Boolean {
        val today = currentCollectionTodayDate(clock.now())
        if (mutableUiState.value.todayDate == today) return false
        mutableUiState.update { it.copy(todayDate = today) }
        return true
    }

    fun refreshToday() {
        if (refreshJob?.isActive == true || todayJob?.isActive == true ||
            mutableUiState.value.isRefreshRequestActive
        ) {
            return
        }
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
        val showTodayLoading = state.today?.date != state.todayDate
        mutableUiState.update {
            it.copy(
                isLoadingToday = showTodayLoading,
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
        val todayRequest = loadToday(showLoading = showTodayLoading)
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
                isCalendarCardSelected = false,
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
        if (state.calendarCards.any { it.id == cardId }) {
            selectCalendarCard(cardId)
        } else {
            selectCalendarDate(cardId)
        }
    }

    fun selectCalendarDate(date: String) {
        val state = mutableUiState.value
        if (!state.calendarLoaded || date.take(7) != state.calendarMonth ||
            date > state.todayDate ||
            collectionMonthDays(state.calendarMonth).none { it.isCurrentMonth && it.date == date }
        ) {
            return
        }
        val card = state.calendarCards.firstOrNull { it.period.take(10).replace('.', '-') == date }
        if (state.day.selectedCalendarDate == date) {
            if (state.day.selectedCalendarCardId != card?.id) {
                mutableUiState.update { it.copy(day = it.day.copy(selectedCalendarCardId = card?.id)) }
            }
            return
        }
        val cachedEmotions = calendarEmotionsByDate[date]
        calendarEmotionsRequestId += 1
        calendarEmotionsJob?.cancel()
        calendarEmotionsCursor = null
        mutableUiState.update {
            it.copy(
                day =
                    CollectionDayUiState(
                        selectedCalendarCardId = card?.id,
                        selectedCalendarDate = date,
                        calendarEmotionsLoaded = cachedEmotions != null,
                        retainEmptyEmotions =
                            cachedEmotions == null && it.day.calendarEmotionsError == null &&
                                (
                                    it.day.retainEmptyEmotions ||
                                        (it.day.calendarEmotionsLoaded && it.day.calendarEmotions.isEmpty())
                                ),
                        isDeletingEmotion = it.day.isDeletingEmotion,
                        pendingReactionIds = pendingReactionIds(date),
                    ).withCalendarEmotions(cachedEmotions.orEmpty()),
            )
        }
        loadCalendarEmotions(date, cursor = null, append = false)
    }

    fun selectCalendarCard(cardId: String) {
        val state = mutableUiState.value
        if (state.calendarCards.none { it.id == cardId } || state.day.selectedCalendarCardId == cardId) return
        state.calendarCards
            .firstOrNull { it.id == cardId }
            ?.period
            ?.take(10)
            ?.replace('.', '-')
            ?.let(::selectCalendarDate)
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
        val date = state.day.selectedCalendarDate ?: return
        val cursor = calendarEmotionsCursor ?: return
        if (!state.day.hasNextCalendarEmotions || state.day.isLoadingCalendarEmotions ||
            state.day.isLoadingNextCalendarEmotions || state.day.pendingReactionIds.isNotEmpty()
        ) {
            return
        }
        loadCalendarEmotions(date, cursor, append = true)
    }

    fun reactToCalendarEmotion(
        emotionId: Long,
        type: EmotionReactionType,
    ) {
        val generation = sessionGeneration ?: return
        val date = mutableUiState.value.day.selectedCalendarDate ?: return
        val emotion =
            mutableUiState.value.day.calendarEmotions
                .firstOrNull { it.id == emotionId } ?: return
        val reaction = emotion.emojis.firstOrNull { it.type == type } ?: return
        if (emotionId in pendingReactions) return
        pendingReactions[emotionId] = date
        val selected = !reaction.selected
        calendarEmotionsRequestId += 1
        calendarEmotionsJob?.cancel()
        calendarEmotionsJob = null
        mutableUiState.update {
            it.copy(
                day =
                    it.day.copy(
                        isLoadingCalendarEmotions = false,
                        isLoadingNextCalendarEmotions = false,
                        pendingReactionIds = pendingReactionIds(date),
                        calendarReactionError = null,
                    ),
            )
        }
        viewModelScope.launch {
            val result =
                try {
                    reactEmotion(emotionId, type, selected, generation)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
                }
            if (sessionGeneration != generation) return@launch
            pendingReactions.remove(emotionId)

            val succeeded = result is EmotionResult.Success
            val updatedEmotions = calendarEmotionsByDate[date].orEmpty()
            val emotions =
                if (succeeded) {
                    updatedEmotions
                        .map { current ->
                            if (current.id == emotionId) current.withReaction(type, selected) else current
                        }.also { calendarEmotionsByDate[date] = it }
                } else {
                    updatedEmotions
                }

            if (mutableUiState.value.day.selectedCalendarDate == date) {
                val remaining = pendingReactionIds(date)
                mutableUiState.update {
                    it.copy(
                        day =
                            it.day
                                .copy(
                                    pendingReactionIds = remaining,
                                    calendarReactionError =
                                        if (succeeded) {
                                            it.day.calendarReactionError
                                        } else {
                                            "이모지 반응을 저장하지 못했어요. 다시 시도해 주세요."
                                        },
                                ).withCalendarEmotions(emotions),
                    )
                }
                if (remaining.isEmpty()) loadCalendarEmotions(date, cursor = null, append = false)
            }
        }
    }

    private fun pendingReactionIds(date: String): Set<Long> = pendingReactions.filterValues { it == date }.keys.toSet()

    private fun CollectionEmotion.withReaction(
        type: EmotionReactionType,
        selected: Boolean,
    ): CollectionEmotion =
        copy(
            emojis =
                emojis.map { current ->
                    if (current.type == type && current.selected != selected) {
                        current.copy(
                            selected = selected,
                            count = (current.count + if (selected) 1 else -1).coerceAtLeast(0),
                        )
                    } else {
                        current
                    }
                },
        )

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
                    deleteEmotion(emotionId, generation)
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
                state.day.selectedCalendarDate?.let { date ->
                    calendarEmotionsByDate[date]?.let { cached ->
                        calendarEmotionsByDate[date] = cached.filterNot { it.id == emotionId }
                    }
                }
                loadCalendar(mutableUiState.value.calendarMonth, refreshSelectedDay = false)
                val date = mutableUiState.value.day.selectedCalendarDate
                if (date == state.day.selectedCalendarDate && date != null) {
                    mutableUiState.update { current ->
                        current.copy(
                            day =
                                current.day.withCalendarEmotions(
                                    current.day.calendarEmotions.filterNot { it.id == emotionId },
                                ),
                        )
                    }
                    loadCalendarEmotions(date, cursor = null, append = false)
                }
            }
        }
    }

    fun retryCalendarEmotions() {
        val state = mutableUiState.value
        val date = state.day.selectedCalendarDate ?: return
        if (calendarEmotionsJob?.isActive == true) return
        loadCalendarEmotions(date, cursor = null, append = false)
    }

    fun retryNextCalendarEmotions() {
        if (calendarEmotionsCursor != null) loadNextCalendarEmotions() else retryCalendarEmotions()
    }

    private fun loadCalendar(
        month: String,
        refreshSelectedDay: Boolean = true,
    ): Job? {
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
                                state.copy(
                                    calendarCards = cards,
                                    calendarLoaded = true,
                                    isLoadingCalendar = false,
                                    calendarError = null,
                                )
                            }
                            val selectedDate = mutableUiState.value.day.selectedCalendarDate
                            if (selectedDate != null) {
                                selectCalendarDate(selectedDate)
                                if (refreshSelectedDay) {
                                    loadCalendarEmotions(
                                        selectedDate,
                                        cursor = null,
                                        append = false,
                                    )
                                }
                            }
                            if (selectedDate == null &&
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
        date: String,
        cursor: String?,
        append: Boolean,
    ) {
        val generation = sessionGeneration ?: return
        if (date in pendingReactions.values) return
        val requestId = ++calendarEmotionsRequestId
        calendarEmotionsJob?.cancel()
        val isSilentRefresh = !append && calendarEmotionsByDate.containsKey(date)
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
                    safeRepositoryCall { repository.findDailyEmotions(date, cursor) }
                }
                val result =
                    if (!append && !isSilentRefresh) {
                        withDelayedLoadingIndicator(
                            onVisibleChanged = { visible ->
                                if (visible && sessionGeneration == generation &&
                                    calendarEmotionsRequestId == requestId &&
                                    mutableUiState.value.day.selectedCalendarDate == date
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
                            mutableUiState.value.day.selectedCalendarDate == date
                        ) {
                            applyCalendarEmotions(date, result.value)
                        }
                    }

                    is CollectionResult.Failure -> {
                        if (sessionGeneration == generation && calendarEmotionsRequestId == requestId &&
                            mutableUiState.value.day.selectedCalendarDate == date
                        ) {
                            if (append && result.reason.code == "COLLECTION-007") {
                                loadCalendarEmotions(date, cursor = null, append = false)
                                return@launch
                            }
                            mutableUiState.update {
                                it.copy(
                                    day =
                                        it.day.copy(
                                            isLoadingCalendarEmotions = false,
                                            isLoadingNextCalendarEmotions = false,
                                            calendarEmotionsError = messageFor(result.reason),
                                            retainEmptyEmotions = false,
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
        date: String,
        page: CollectionEmotionPage,
    ) {
        calendarEmotionsCursor = page.nextCursor
        refreshedCalendarEmotions = (refreshedCalendarEmotions + page.items).distinctBy { it.id }
        // Keep the cached tail visible until pagination reaches the end of the fresh list.
        val emotions =
            if (page.hasNext) {
                (refreshedCalendarEmotions + calendarEmotionsByDate[date].orEmpty()).distinctBy { it.id }
            } else {
                refreshedCalendarEmotions
            }
        calendarEmotionsByDate[date] = emotions
        mutableUiState.update { state ->
            state.copy(
                day =
                    state.day
                        .copy(
                            calendarEmotionsLoaded = true,
                            retainEmptyEmotions = false,
                            isLoadingCalendarEmotions = false,
                            isLoadingNextCalendarEmotions = false,
                            calendarEmotionsError = null,
                            hasNextCalendarEmotions = page.hasNext,
                        ).withCalendarEmotions(emotions),
            )
        }
    }

    fun selectCard(cardId: String) = selectCard(cardId, fromCalendar = false)

    fun selectCalendarCardDetail(cardId: String) = selectCard(cardId, fromCalendar = true)

    private fun selectCard(
        cardId: String,
        fromCalendar: Boolean,
    ) {
        val generation = sessionGeneration ?: return
        val requestId = ++detailRequestId
        detailJob?.cancel()
        mutableUiState.update {
            val cards = if (fromCalendar) it.calendarCards else it.cards
            it.copy(
                selectedCardId = cardId,
                isCalendarCardSelected = fromCalendar,
                selectedCard = cards.firstOrNull { card -> card.id == cardId },
                isLoadingCard = true,
                cardError = null,
            )
        }
        detailJob =
            viewModelScope.launch {
                when (val result = safeRepositoryCall { repository.findCard(cardId) }) {
                    is CollectionResult.Success -> {
                        if (sessionGeneration == generation && detailRequestId == requestId &&
                            mutableUiState.value.selectedCardId == cardId
                        ) {
                            mutableUiState.update { state ->
                                state.copy(selectedCard = result.value.toUiModel(state.nickname), isLoadingCard = false)
                            }
                        }
                    }

                    is CollectionResult.Failure -> {
                        if (sessionGeneration == generation && detailRequestId == requestId &&
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
        detailRequestId += 1
        detailJob?.cancel()
        mutableUiState.update {
            it.copy(
                selectedCardId = null,
                isCalendarCardSelected = false,
                selectedCard = null,
                isLoadingCard = false,
                cardError = null,
            )
        }
    }

    fun retrySelectedCard() {
        val state = mutableUiState.value
        state.selectedCardId?.let { selectCard(it, fromCalendar = state.isCalendarCardSelected) }
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
                    val nickname = result.value.nickname ?: state.nickname
                    val title = nickname?.takeIf(String::isNotBlank) ?: "나"
                    val cards = result.value.cards.map { it.toUiModel(nickname) }
                    val updatedCards =
                        if (append) (state.cards + cards).distinctBy(CollectionCardUiModel::id) else cards
                    val selectionRemoved =
                        !state.isCalendarCardSelected && state.selectedCardId != null &&
                            updatedCards.none { it.id == state.selectedCardId }
                    state.copy(
                        nickname = nickname,
                        calendarCards =
                            if (nickname !=
                                state.nickname
                            ) {
                                state.calendarCards.map { it.copy(title = title) }
                            } else {
                                state.calendarCards
                            },
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
                        selectedCard = if (selectionRemoved) null else state.selectedCard?.copy(title = title),
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
            collectionDate = reference.date,
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

            "COLLECTION-007" -> {
                "목록이 변경되어 다시 불러와야 해요."
            }

            "COLLECTION-008" -> {
                "목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
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

internal val CollectionUiState.todayProgress: CollectionProgressUiModel?
    get() = today.toTodayProgress(claimingState)

private fun CollectionToday?.toTodayProgress(claimingState: EmotionType?): CollectionProgressUiModel? =
    this
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
        }

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
        pendingProgress = todayProgress,
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
        todayDate = todayDate,
        calendarMonth = calendarMonth,
        calendarCards = calendarCards,
        isLoadingCalendar = isLoadingCalendar,
        calendarError = calendarError,
    )

private fun CollectionDayUiState.withCalendarEmotions(emotions: List<CollectionEmotion>): CollectionDayUiState =
    copy(emotionSnapshot = CollectionDayEmotionSnapshot(emotions))

private fun CollectionEmotion.toUiModel(): CollectionDayEmotionUiModel =
    CollectionDayEmotionUiModel(
        id = id,
        timeLabel = formatCollectionEmotionTime(createdAt),
        emotion = state.toUi(),
        contentType = contentType,
        memo = memo,
        playbackUrl = audio?.playbackUrl,
        playbackExpiresAt = audio?.expiresAt,
        emojis = emojis.map { CollectionDayEmojiUiModel(it.type, it.count, it.selected) },
    )

private fun formatCollectionEmotionTime(createdAt: String): String {
    val timestamp = Instant.parse(createdAt)
    val seconds = (Clock.System.now() - timestamp).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> {
            "방금 전"
        }

        seconds < 3600 -> {
            "${seconds / 60}분 전"
        }

        seconds < 86400 -> {
            "${seconds / 3600}시간 전"
        }

        seconds < 4 * 86400 -> {
            "${seconds / 86400}일 전"
        }

        else -> {
            val kst = Instant.fromEpochMilliseconds(timestamp.toEpochMilliseconds() + 9 * 60 * 60 * 1000L)
            kst.toString().substringBefore('T').replace('-', '.')
        }
    }
}
