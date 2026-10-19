package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.api.GroupRankingApi
import com.pheeeew.data.remote.mapper.GroupRankingMapper
import com.pheeeew.domain.repository.GroupRankingLoadResult
import com.pheeeew.domain.repository.GroupRankingRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

@Inject
class GroupRankingRepositoryImpl(
    private val api: GroupRankingApi,
) : GroupRankingRepository {
    override suspend fun find(weeksAgo: Int): GroupRankingLoadResult =
        when (val result = api.find(weeksAgo)) {
            is ApiResult.Success -> {
                try {
                    GroupRankingLoadResult.Loaded(GroupRankingMapper.toDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupRankingLoadResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                GroupRankingLoadResult.Unavailable
            }
        }
}
