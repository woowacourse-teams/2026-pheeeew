package com.pheeeew.feature.screens.collection

import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.calendar.currentCollectionMonth
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel

enum class CollectionCardActionError { CAPTURE, SAVE, SHARE }

internal val collectionCoverFlowEmotionOrder =
    listOf(
        EmotionType.FRUSTRATED,
        EmotionType.IRRITATED,
        EmotionType.EXHAUSTED,
        EmotionType.DISCOURAGED,
        EmotionType.ANGRY,
    )

data class CollectionScreenUiModel(
    val nickname: String,
    val cards: List<CollectionCardUiModel>,
    val isNotificationDateOutdated: Boolean = false,
    val totalCount: Long = cards.size.toLong(),
    val emotionCounts: Map<EmotionType, Long> = emptyMap(),
    val selectedFilter: EmotionType? = null,
    val pendingProgress: CollectionProgressUiModel? = null,
    val hasClaimedToday: Boolean = false,
    val todayCardId: String? = null,
    val todayLoading: Boolean = false,
    val todayError: String? = null,
    val isLoadingCards: Boolean = false,
    val isRefreshRequestActive: Boolean = false,
    val isRefreshIndicatorVisible: Boolean = false,
    val scrollToTopRequestId: Long = 0,
    val cardsError: String? = null,
    val isLoadingNext: Boolean = false,
    val hasNext: Boolean = false,
    val claimError: String? = null,
    val claimOutcomeUnknown: Boolean = false,
    val selectedCard: CollectionCardUiModel? = null,
    val isLoadingCard: Boolean = false,
    val cardError: String? = null,
    val saveCompletionCardId: String? = null,
    val saveCompletionId: Int = 0,
    val imageActionError: CollectionCardActionError? = null,
    val imageActionErrorCardId: String? = null,
    val imageActionErrorId: Int = 0,
    val imageActionPhase: CollectionCardExportPhase = CollectionCardExportPhase.IDLE,
    val calendarMonth: String = currentCollectionMonth(),
    val calendarCards: List<CollectionCardUiModel> = emptyList(),
    val isLoadingCalendar: Boolean = false,
    val calendarError: String? = null,
    val commonError: CollectionCommonError? = null,
    val appendError: String? = null,
    val historyLoaded: Boolean = false,
    val calendarLoaded: Boolean = false,
    val todayLoaded: Boolean = false,
    val deferSectionErrors: Boolean = false,
) {
    internal val coverFlowItems: List<CollectionCarouselItem> =
        buildList {
            when {
                pendingProgress != null -> {
                    add(CollectionCarouselItem(id = "pending", progress = pendingProgress))
                }

                hasClaimedToday && (cards.isNotEmpty() || cardsError == null) -> {
                    add(CollectionCarouselItem(id = "tomorrow", isTomorrow = true))
                }
            }
            cards.forEach { card -> add(CollectionCarouselItem(id = card.id, card = card)) }
        }

    internal val coverflowLoad =
        sectionLoad(
            hasContent = historyLoaded || todayLoaded || coverFlowItems.isNotEmpty(),
            loading = isLoadingCards || todayLoading,
            failure =
                cardsError?.let { CollectionSectionFailure(it, CollectionRetryTarget.History) }
                    ?: todayError?.let { CollectionSectionFailure(it, CollectionRetryTarget.Today) },
        )

    internal val calendarLoad =
        sectionLoad(
            hasContent = calendarLoaded || calendarCards.isNotEmpty(),
            loading = isLoadingCalendar,
            failure = calendarError?.let { CollectionSectionFailure(it, CollectionRetryTarget.Calendar) },
        )

    private fun sectionLoad(
        hasContent: Boolean,
        loading: Boolean,
        failure: CollectionSectionFailure?,
    ): CollectionSectionLoad {
        val visibleFailure = failure.takeIf { commonError == null && !deferSectionErrors }
        return when {
            hasContent -> {
                CollectionSectionLoad.Content(
                    refreshing = loading,
                    refreshFailure = visibleFailure,
                )
            }

            loading || (failure != null && deferSectionErrors) -> {
                CollectionSectionLoad.InitialLoading
            }

            visibleFailure != null -> {
                CollectionSectionLoad.InitialFailure(visibleFailure)
            }

            else -> {
                CollectionSectionLoad.Content()
            }
        }
    }
}

internal data class CollectionCarouselItem(
    val id: String,
    val card: CollectionCardUiModel? = null,
    val progress: CollectionProgressUiModel? = null,
    val isTomorrow: Boolean = false,
)

data class CollectionDayEmotionUiModel(
    val id: Long,
    val createdAt: String,
    val emotion: EmotionType,
    val contentType: CollectionEmotionContentType,
    val memo: String?,
    val playbackUrl: String?,
    val playbackExpiresAt: String?,
)

data class CollectionProgressUiModel(
    val emotion: EmotionType,
    val stampCount: Long,
    val stampTarget: Long,
    val stampRemaining: Long,
    val pressCount: Long,
    val pressTarget: Long,
    val pressRemaining: Long,
    val canClaim: Boolean,
    val stampProgressByEmotion: List<CollectionEmotionProgress> = emptyList(),
    val pressProgressByEmotion: List<CollectionEmotionProgress> = emptyList(),
    val hasProgress: Boolean = true,
    val isClaiming: Boolean = false,
)

data class CollectionEmotionProgress(
    val emotion: EmotionType,
    val count: Long,
)

enum class CollectionCommonError { Initial, Refresh }

internal enum class CollectionRetryTarget { History, Today, Calendar }

internal data class CollectionSectionFailure(
    val message: String,
    val retryTarget: CollectionRetryTarget,
)

internal sealed interface CollectionSectionLoad {
    data object InitialLoading : CollectionSectionLoad

    data class InitialFailure(
        val failure: CollectionSectionFailure,
    ) : CollectionSectionLoad

    data class Content(
        val refreshing: Boolean = false,
        val refreshFailure: CollectionSectionFailure? = null,
    ) : CollectionSectionLoad
}
