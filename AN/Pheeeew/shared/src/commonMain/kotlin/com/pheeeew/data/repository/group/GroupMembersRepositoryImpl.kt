package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.group.api.GroupMembersApi
import com.pheeeew.data.remote.group.mapper.GroupMembersResponseMapper
import com.pheeeew.domain.repository.group.GroupMembersRepository
import com.pheeeew.domain.repository.group.GroupMembersResult
import kotlinx.coroutines.CancellationException
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class GroupMembersRepositoryImpl(
    private val api: GroupMembersApi,
) : GroupMembersRepository {
    override suspend fun loadPage(
        groupId: DomainGroupId,
        cursor: String?,
    ): GroupMembersResult =
        when (val result = api.loadPage(groupId.value, cursor)) {
            is ApiResult.Success -> {
                try {
                    GroupMembersResult.Loaded(GroupMembersResponseMapper.toDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupMembersResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                when (val reason = result.reason) {
                    is NetworkFailure.HttpStatus -> {
                        when {
                            reason.statusCode == 403 && reason.error?.code == "GROUP-008" -> {
                                GroupMembersResult.Forbidden
                            }

                            reason.statusCode == 404 && reason.error?.code == "GROUP-001" -> {
                                GroupMembersResult.NotFound
                            }

                            reason.statusCode == 400 && reason.error?.code == "GROUP-010" -> {
                                GroupMembersResult.InvalidCursor
                            }

                            reason.statusCode == 401 -> {
                                GroupMembersResult.Unauthorized
                            }

                            else -> {
                                GroupMembersResult.Unavailable
                            }
                        }
                    }

                    is NetworkFailure.SessionUnavailable,
                    is NetworkFailure.SessionProviderFailed,
                    -> {
                        GroupMembersResult.Unauthorized
                    }

                    else -> {
                        GroupMembersResult.Unavailable
                    }
                }
            }
        }
}
