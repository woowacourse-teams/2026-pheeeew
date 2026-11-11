package com.pheeeew.domain.model.pressevent

import com.pheeeew.domain.model.emotion.EmotionState

internal object PressEventRules {
    const val COUNTDOWN_MILLIS = 3_000L
    const val DURATION_MILLIS = 20_000L
}

/** Frozen payload shared by every retry of one completed attempt. */
internal class PressEventAttempt(
    requestId: String,
    counts: Map<EmotionState, Int>,
) {
    val requestId = requestId.also { require(it.isNotBlank()) }
    val counts = EmotionState.entries.associateWith { counts[it] ?: 0 }
    val total = this.counts.values.sumOf { it.toLong() }

    init {
        require(this.counts.values.all { it >= 0 })
    }
}

internal data class PressEventAttemptResult(
    val attemptId: String,
    val total: Long,
    val bestScore: Long,
    val rank: Long,
    val rankedAt: String,
    val replayed: Boolean,
)

internal data class PressEventLeader(
    val publicId: String,
    val nickname: String,
    val bestScore: Long,
)

internal data class PressEventRankingEntry(
    val rank: Long,
    val publicId: String,
    val nickname: String,
    val bestScore: Long,
    val mine: Boolean,
)

internal data class PressEventMyRank(
    val bestScore: Long,
    val rank: Long,
)

internal data class PressEventRankingPage(
    val leader: PressEventLeader?,
    val items: List<PressEventRankingEntry>,
    val hasNext: Boolean,
    val nextCursor: String?,
    val me: PressEventMyRank?,
    val rankedAt: String,
)
