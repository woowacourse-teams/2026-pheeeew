package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.api.GroupMembershipApi
import com.pheeeew.data.remote.mapper.GroupMembersResponseMapper
import com.pheeeew.domain.repository.GroupMembersRepository
import com.pheeeew.domain.repository.GroupMembersResult
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

@Inject
class GroupMembersRepositoryImpl(
    private val api: GroupMembershipApi,
) : GroupMembersRepository {
    override suspend fun loadPage(
        groupId: DomainGroupId,
        cursor: String?,
    ): GroupMembersResult =
        when (val result = api.members(groupId.value, cursor)) {
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
