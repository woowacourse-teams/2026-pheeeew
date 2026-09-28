package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.group.api.GroupStampListApi
import com.pheeeew.data.remote.group.mapper.GroupResponseMapper
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import kotlinx.coroutines.CancellationException

class GroupStampListRepositoryImpl(
    private val api: GroupStampListApi,
) : GroupStampListRepository {
    override suspend fun findMine(): GroupStampListLoadResult =
        when (val result = api.findMine()) {
            is ApiResult.Success -> {
                try {
                    GroupStampListLoadResult.Loaded(result.value.map(GroupResponseMapper::toDomain))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupStampListLoadResult.Unavailable
                }
            }

            is ApiResult.Failure -> GroupStampListLoadResult.Unavailable
        }
}
