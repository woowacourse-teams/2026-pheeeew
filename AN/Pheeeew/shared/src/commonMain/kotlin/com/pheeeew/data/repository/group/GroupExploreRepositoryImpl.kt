package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.group.api.GroupExploreApi
import com.pheeeew.data.remote.group.mapper.GroupExploreResponseMapper
import com.pheeeew.domain.repository.group.GroupExploreRepository
import com.pheeeew.domain.repository.group.GroupExploreResult
import kotlinx.coroutines.CancellationException

class GroupExploreRepositoryImpl(
    private val api: GroupExploreApi,
) : GroupExploreRepository {
    override suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): GroupExploreResult =
        when (val result = api.loadPage(keyword, cursor)) {
            is ApiResult.Success -> {
                try {
                    GroupExploreResult.Loaded(GroupExploreResponseMapper.toDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupExploreResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                val reason = result.reason
                if (reason is NetworkFailure.HttpStatus && reason.statusCode == 400 &&
                    reason.error?.code == "GROUP-010"
                ) {
                    GroupExploreResult.InvalidCursor
                } else {
                    GroupExploreResult.Unavailable
                }
            }
        }
}
