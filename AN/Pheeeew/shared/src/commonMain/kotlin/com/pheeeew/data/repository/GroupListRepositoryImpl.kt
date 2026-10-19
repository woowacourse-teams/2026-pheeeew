package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.api.GroupApi
import com.pheeeew.data.remote.mapper.GroupResponseMapper
import com.pheeeew.domain.repository.GroupListLoadResult
import com.pheeeew.domain.repository.GroupListRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

@Inject
class GroupListRepositoryImpl(
    private val api: GroupApi,
) : GroupListRepository {
    override suspend fun findMine(): GroupListLoadResult =
        when (val result = api.findMine()) {
            is ApiResult.Success -> {
                try {
                    GroupListLoadResult.Loaded(result.value.map(GroupResponseMapper::toDomain))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupListLoadResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                GroupListLoadResult.Unavailable
            }
        }
}
