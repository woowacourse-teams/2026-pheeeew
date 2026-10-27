package com.pheeeew.feature.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.utils.withDelayedLoadingIndicator
import com.pheeeew.domain.model.collection.CollectionCard
import com.pheeeew.domain.model.collection.CollectionFailure
import com.pheeeew.domain.model.collection.CollectionHistoryQuery
import com.pheeeew.domain.model.collection.CollectionResult
import com.pheeeew.domain.model.collection.CollectionToday
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.CollectionRepository
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
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
)

/** Owns collection reads, the idempotent claim intent, and detail loading for this root tab. */
@Inject
internal class CollectionViewModel(
    private val repository: CollectionRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CollectionUiState())
    val uiState = mutableUiState.asStateFlow()

    private var sessionGeneration: Long? = null
    private var todayJob: Job? = null
    private var historyJob: Job? = null
    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var notificationRefreshPending = false
    private var refreshRequestId = 0L
    private var todayRequestId = 0L
    private var historyRequestId = 0L
    private var historyCursor: String? = null
    private val historyByFilter = mutableMapOf<EmotionType?, HistorySnapshot>()
    private var pendingClaim: ClaimIntent? = null

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
        refreshJob?.cancel()
        todayJob?.cancel()
        historyJob?.cancel()
        detailJob?.cancel()
        historyCursor = null
        historyByFilter.clear()
        pendingClaim = null
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
                                it.copy(today = result.value, isLoadingToday = false, todayError = null)
                            }
                        }
                    }

                    is CollectionResult.Failure -> {
                        if (sessionGeneration == generation && todayRequestId == requestId) {
                            mutableUiState.update {
                                it.copy(todayError = messageFor(result.reason), isLoadingToday = false)
                            }
                        }
                    }
                }
            }
        todayJob = job
        return job
    }

    fun retryCards() {
        refresh()
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
        if (refreshJob?.isActive == true) {
            if (!cancelInFlight) {
                if (resetToTop) {
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
                isLoadingCards = it.cards.isEmpty(),
                cardsError = null,
                isLoadingNext = false,
                isRefreshRequestActive = true,
                isRefreshIndicatorVisible = false,
                scrollToTopRequestId =
                    if (resetToTop) it.scrollToTopRequestId + 1 else it.scrollToTopRequestId,
            )
        }
        val todayRequest = loadToday(showLoading = state.today == null)
        val historyRequest = loadFirstHistoryPage(filter)
        refreshJob =
            viewModelScope.launch {
                try {
                    val waitForRequests: suspend () -> Unit = {
                        listOfNotNull(todayRequest, historyRequest).joinAll()
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
        mutableUiState.update { it.copy(isLoadingNext = true, cardsError = null) }
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
                    refresh(resetToTop = false, showIndicator = false, cancelInFlight = true)
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
        mutableUiState.update { it.copy(isLoadingCards = true) }
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
                mutableUiState.update { state ->
                    val cards = result.value.cards.map { it.toUiModel(result.value.nickname ?: state.nickname) }
                    val updatedCards =
                        if (append) (state.cards + cards).distinctBy(CollectionCardUiModel::id) else cards
                    val selectionRemoved =
                        state.selectedCardId != null && updatedCards.none { it.id == state.selectedCardId }
                    state.copy(
                        nickname = result.value.nickname ?: state.nickname,
                        cards = updatedCards,
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
                        cardsError = messageFor(result.reason),
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
            pressLabel = "${reference.state.toUi().labelFallback()} 프레스",
            pressCount = pressCount,
            stampLabel = "${reference.state.toUi().labelFallback()} 스탬프",
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
            CollectionResult.Failure(CollectionFailure(null, "NETWORK", null))
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

private fun EmotionType.labelFallback(): String =
    when (this) {
        EmotionType.FRUSTRATED -> "답답"
        EmotionType.IRRITATED -> "짜증"
        EmotionType.EXHAUSTED -> "지침"
        EmotionType.DISCOURAGED -> "좌절"
        EmotionType.ANGRY -> "분노"
    }
