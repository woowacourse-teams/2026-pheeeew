package com.pheeeew.domain.repository

import com.pheeeew.domain.model.ranking.DailyPressRanking
import com.pheeeew.domain.model.ranking.PressRanking

interface PressRankingRepository {
    suspend fun find(
        weeksAgo: Int,
        state: String?,
    ): PressRankingLoadResult

    suspend fun findDaily(
        date: String,
        expectedSessionGeneration: Long? = null,
    ): DailyPressRankingLoadResult = DailyPressRankingLoadResult.Unavailable
}

sealed interface DailyPressRankingLoadResult {
    data class Loaded(
        val ranking: DailyPressRanking,
    ) : DailyPressRankingLoadResult

    data object Unavailable : DailyPressRankingLoadResult
}

sealed interface PressRankingLoadResult {
    data class Loaded(
        val ranking: PressRanking,
    ) : PressRankingLoadResult

    data object Unavailable : PressRankingLoadResult
}
