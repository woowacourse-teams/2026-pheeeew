package com.pheeeew.feature.screens.ranking

interface WeeklyRankingSource {
    suspend fun load(weeksAgo: Int): WeeklyRankingLoadResult
}

sealed interface WeeklyRankingLoadResult {
    data class Loaded(
        val startAt: String,
        val endAt: String,
        val hasPrevious: Boolean,
        val rankings: List<RankingMember>,
    ) : WeeklyRankingLoadResult

    data object Unavailable : WeeklyRankingLoadResult
}
