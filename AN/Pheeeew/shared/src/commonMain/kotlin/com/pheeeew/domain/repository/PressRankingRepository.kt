package com.pheeeew.domain.repository

import com.pheeeew.domain.model.ranking.PressRanking

interface PressRankingRepository {
    suspend fun find(
        weeksAgo: Int,
        state: String?,
    ): PressRankingLoadResult
}

sealed interface PressRankingLoadResult {
    data class Loaded(
        val ranking: PressRanking,
    ) : PressRankingLoadResult

    data object Unavailable : PressRankingLoadResult
}
