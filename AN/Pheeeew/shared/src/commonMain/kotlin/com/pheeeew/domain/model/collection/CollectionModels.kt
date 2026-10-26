package com.pheeeew.domain.model.collection

import com.pheeeew.domain.model.emotion.EmotionState

data class CollectionImages(
    val characterUrl: String,
    val roundStampUrl: String,
    val straightStampUrl: String,
)

data class CollectionCardReference(
    val cardId: String,
    val date: String,
    val state: EmotionState,
    val images: CollectionImages,
)

data class CollectionCard(
    val reference: CollectionCardReference,
    val stampCount: Long,
    val pressCount: Long,
)

data class CollectionActivityProgress(
    val count: Long,
    val target: Long,
    val remaining: Long,
)

data class CollectionTodayItem(
    val state: EmotionState,
    val stamps: CollectionActivityProgress,
    val presses: CollectionActivityProgress,
    val card: CollectionCardReference?,
) {
    val isClaimed: Boolean
        get() = card != null
}

data class CollectionToday(
    val date: String,
    val items: List<CollectionTodayItem>,
) {
    val totalStampCount: Long
        get() = items.sumOf { it.stamps.count }

    val totalPressCount: Long
        get() = items.sumOf { it.presses.count }

    val hasClaimedCard: Boolean
        get() = items.any(CollectionTodayItem::isClaimed)

    val claimEmotion: EmotionState?
        get() =
            EmotionState.entries
                .mapNotNull { emotion -> items.firstOrNull { it.state == emotion } }
                .maxByOrNull { item -> item.presses.count + (item.stamps.count * COLLECTION_PRESS_PER_STAMP_WEIGHT) }
                ?.state

    val canClaim: Boolean
        get() =
            !hasClaimedCard &&
                totalStampCount >= COLLECTION_STAMP_CLAIM_TARGET &&
                totalPressCount >= COLLECTION_PRESS_CLAIM_TARGET
}

data class CollectionCounts(
    val total: Long,
    val byEmotion: Map<EmotionState, Long>,
)

data class CollectionHistory(
    val nickname: String?,
    val cards: List<CollectionCard>,
    val counts: CollectionCounts,
    val hasNext: Boolean,
    val nextCursor: String?,
)

data class CollectionHistoryQuery(
    val emotion: EmotionState? = null,
    val limit: Int = DEFAULT_COLLECTION_PAGE_SIZE,
    val cursor: String? = null,
) {
    init {
        require(limit in MIN_COLLECTION_PAGE_SIZE..MAX_COLLECTION_PAGE_SIZE)
    }
}

const val DEFAULT_COLLECTION_PAGE_SIZE = 20
const val MIN_COLLECTION_PAGE_SIZE = 1
const val MAX_COLLECTION_PAGE_SIZE = 50

internal const val COLLECTION_STAMP_CLAIM_TARGET = 1L
internal const val COLLECTION_PRESS_CLAIM_TARGET = 100L
internal const val COLLECTION_PRESS_PER_STAMP_WEIGHT = 10L

data class CollectionFailure(
    val statusCode: Int?,
    val code: String?,
    val message: String?,
    val isOutcomeUnknown: Boolean = false,
)

sealed interface CollectionResult<out T> {
    data class Success<T>(
        val value: T,
    ) : CollectionResult<T>

    data class Failure(
        val reason: CollectionFailure,
    ) : CollectionResult<Nothing>
}
