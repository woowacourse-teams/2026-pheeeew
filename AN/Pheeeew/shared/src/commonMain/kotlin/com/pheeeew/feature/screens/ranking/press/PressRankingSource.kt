package com.pheeeew.feature.screens.ranking.press

internal interface PressRankingSource {
    suspend fun load(
        emotion: PressEmotion,
        weeksAgo: Int,
    ): PressRankingLoadResult
}

internal sealed interface PressRankingLoadResult {
    data class Loaded(
        val startAt: String,
        val endAt: String,
        val hasPrevious: Boolean,
        val people: List<PressPersonRank>,
        val me: PressPersonRank? = null,
    ) : PressRankingLoadResult

    data object Unavailable : PressRankingLoadResult
}
