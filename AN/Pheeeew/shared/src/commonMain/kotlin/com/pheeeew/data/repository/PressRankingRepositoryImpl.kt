package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.api.PressRankingApi
import com.pheeeew.data.remote.mapper.PressRankingMapper
import com.pheeeew.domain.repository.DailyPressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

@Inject
class PressRankingRepositoryImpl(
    private val api: PressRankingApi,
) : PressRankingRepository {
    override suspend fun findDaily(
        date: String,
        expectedSessionGeneration: Long?,
    ): DailyPressRankingLoadResult =
        when (val result = api.findDaily(date, expectedSessionGeneration)) {
            is ApiResult.Success -> {
                try {
                    DailyPressRankingLoadResult.Loaded(PressRankingMapper.toDailyDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    DailyPressRankingLoadResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                DailyPressRankingLoadResult.Unavailable
            }
        }

    override suspend fun find(
        weeksAgo: Int,
        state: String?,
    ): PressRankingLoadResult =
        when (val result = api.find(weeksAgo, state)) {
            is ApiResult.Success -> {
                try {
                    PressRankingLoadResult.Loaded(PressRankingMapper.toDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    PressRankingLoadResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                PressRankingLoadResult.Unavailable
            }
        }
}
