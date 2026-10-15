package com.pheeeew.feature.screens.ranking.press.data

import com.pheeeew.domain.model.ranking.PressRankingItem
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
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
                val ranking = result.ranking
                PressRankingLoadResult.Loaded(
                    startAt = ranking.startAt,
                    endAt = ranking.endAt,
                    hasPrevious = ranking.hasPrevious,
                    // The API omits identifiers; duplicate anonymous nicknames cannot identify the current device.
                    people = ranking.items.map { it.toUiModel() },
                    me = ranking.me?.toUiModel(),
                )
            }

            RepositoryResult.Unavailable -> {
                PressRankingLoadResult.Unavailable
            }
        }

    private fun PressRankingItem.toUiModel() = PressPersonRank(rank, nickname, pressCount)
}
