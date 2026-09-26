package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.group.GroupListApi
import com.pheeeew.data.remote.group.GroupResponseMapper
import com.pheeeew.domain.model.group.Group
import com.pheeeew.domain.repository.GroupListLoadResult
import com.pheeeew.domain.repository.GroupListRepository
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
