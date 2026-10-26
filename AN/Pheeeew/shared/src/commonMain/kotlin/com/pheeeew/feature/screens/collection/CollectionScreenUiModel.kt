package com.pheeeew.feature.screens.collection

import com.pheeeew.feature.emotion.model.EmotionType
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
