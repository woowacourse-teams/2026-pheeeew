package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.PressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.group.detail.GroupDetailEmotionRankingResult
import com.pheeeew.feature.screens.group.detail.GroupDetailEmotionRankingSource
import com.pheeeew.feature.screens.group.model.GroupId

/** Loads the current all-emotion weekly ranking and selects the requested group without recalculating rank. */
class ApiGroupDetailEmotionRankingSource(
    private val repository: PressRankingRepository,
) : GroupDetailEmotionRankingSource {
    override suspend fun load(groupId: GroupId): GroupDetailEmotionRankingResult =
        when (val result = repository.find(weeksAgo = CURRENT_WEEK, state = null)) {
            is PressRankingLoadResult.Loaded -> {
                val groupRanking =
                    result.ranking.items.firstOrNull { item ->
                        item.groupId.equals(groupId.value, ignoreCase = true)
                    }
                when {
                    groupRanking == null -> {
                        GroupDetailEmotionRankingResult.NotListed
                    }

                    groupRanking.rank <= 0 || groupRanking.score < 0 -> {
                        GroupDetailEmotionRankingResult.Unavailable
                    }

                    groupRanking.score == 0 -> {
                        GroupDetailEmotionRankingResult.NoPresses
                    }

                    else -> {
                        GroupDetailEmotionRankingResult.Ranked(
                            rank = groupRanking.rank,
                            score = groupRanking.score,
                        )
                    }
                }
            }

            PressRankingLoadResult.Unavailable -> {
                GroupDetailEmotionRankingResult.Unavailable
            }
        }

    private companion object {
        const val CURRENT_WEEK = 0
    }
}
