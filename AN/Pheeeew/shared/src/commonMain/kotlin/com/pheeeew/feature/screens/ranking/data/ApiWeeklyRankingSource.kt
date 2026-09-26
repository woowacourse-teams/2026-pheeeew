package com.pheeeew.feature.screens.ranking.data

import com.pheeeew.domain.repository.GroupRankingLoadResult
import com.pheeeew.domain.repository.GroupRankingRepository
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.toUiShape
import com.pheeeew.feature.screens.ranking.RankingMember
import com.pheeeew.feature.screens.ranking.WeeklyRankingLoadResult
import com.pheeeew.feature.screens.ranking.WeeklyRankingSource

class ApiWeeklyRankingSource(
    private val repository: GroupRankingRepository,
) : WeeklyRankingSource {
    override suspend fun load(weeksAgo: Int): WeeklyRankingLoadResult =
        when (val result = repository.find(weeksAgo)) {
            is GroupRankingLoadResult.Loaded -> {
                val ranking = result.ranking
                WeeklyRankingLoadResult.Loaded(
                    startAt = ranking.startAt,
                    endAt = ranking.endAt,
                    hasPrevious = ranking.hasPrevious,
                    rankings =
                        ranking.items.map { item ->
                            RankingMember(
                                rank = item.rank,
                                name = item.name,
                                score = item.score,
                                stamp =
                                    StampAppearanceUiModel(
                                        label = item.stamp.text,
                                        shape = item.stamp.frame.toUiShape(),
                                        fillArgb = item.stamp.backgroundColor.argb,
                                        textArgb = item.stamp.textColor.argb,
                                    ),
                            )
                        },
                )
            }

            GroupRankingLoadResult.Unavailable -> WeeklyRankingLoadResult.Unavailable
        }
}
