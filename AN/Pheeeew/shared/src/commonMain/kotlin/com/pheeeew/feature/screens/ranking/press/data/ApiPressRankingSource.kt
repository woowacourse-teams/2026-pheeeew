package com.pheeeew.feature.screens.ranking.press.data

import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressGroupRank
import com.pheeeew.feature.screens.ranking.press.PressRankingLoadResult
import com.pheeeew.feature.screens.ranking.press.PressRankingSource
import com.pheeeew.domain.repository.PressRankingLoadResult as RepositoryResult

internal class ApiPressRankingSource(
    private val repository: PressRankingRepository,
) : PressRankingSource {
    override suspend fun load(
        emotion: PressEmotion,
        weeksAgo: Int,
    ): PressRankingLoadResult =
        when (val result = repository.find(weeksAgo, emotion.apiState)) {
            is RepositoryResult.Loaded -> {
                PressRankingLoadResult.Loaded(
                    startAt = result.ranking.startAt,
                    endAt = result.ranking.endAt,
                    hasPrevious = result.ranking.hasPrevious,
                    groups =
                        result.ranking.items.map { item ->
                            PressGroupRank(
                                groupId = item.groupId,
                                rank = item.rank,
                                groupName = item.name,
                                subtitle = if (item.mine) "내 그룹" else "함께 누른 마음",
                                count = item.score,
                                isMyGroup = item.mine,
                            )
                        },
                )
            }

            RepositoryResult.Unavailable -> {
                PressRankingLoadResult.Unavailable
            }
        }
}
