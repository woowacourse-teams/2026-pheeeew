package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.api.GroupApi
import com.pheeeew.data.remote.mapper.GroupExploreResponseMapper
import com.pheeeew.domain.repository.GroupExploreRepository
import com.pheeeew.domain.repository.GroupExploreResult
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

@Inject
class GroupExploreRepositoryImpl(
    private val api: GroupApi,
) : GroupExploreRepository {
    override suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): GroupExploreResult =
        when (val result = api.explore(keyword, cursor)) {
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
