package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.group.PressRankingApi
import com.pheeeew.data.remote.group.PressRankingMapper
import com.pheeeew.domain.repository.PressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingRepository
import kotlinx.coroutines.CancellationException

class PressRankingRepositoryImpl(
    private val api: PressRankingApi,
) : PressRankingRepository {
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
