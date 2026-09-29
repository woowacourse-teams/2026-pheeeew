package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.group.api.GroupListApi
import com.pheeeew.data.remote.group.mapper.GroupResponseMapper
import com.pheeeew.domain.repository.group.GroupListLoadResult
import com.pheeeew.domain.repository.group.GroupListRepository
import kotlinx.coroutines.CancellationException

class GroupListRepositoryImpl(
    private val api: GroupListApi,
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
