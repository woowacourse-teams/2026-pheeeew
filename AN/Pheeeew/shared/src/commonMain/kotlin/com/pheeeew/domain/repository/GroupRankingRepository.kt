package com.pheeeew.domain.repository

import com.pheeeew.domain.model.ranking.GroupRanking

interface GroupRankingRepository {
    suspend fun find(weeksAgo: Int): GroupRankingLoadResult
}

sealed interface GroupRankingLoadResult {
    data class Loaded(
        val ranking: GroupRanking,
    ) : GroupRankingLoadResult

    data object Unavailable : GroupRankingLoadResult
}
