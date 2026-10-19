package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.api.GroupMembershipApi
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.repository.GroupPublicJoinRepository
import com.pheeeew.domain.repository.GroupPublicJoinResult
import dev.zacsweers.metro.Inject

@Inject
class GroupPublicJoinRepositoryImpl(
    private val api: GroupMembershipApi,
) : GroupPublicJoinRepository {
    override suspend fun join(groupId: GroupId): GroupPublicJoinResult =
        when (val result = api.joinPublic(groupId.value)) {
            is ApiResult.Success -> {
                if (GroupId.parse(result.value.groupId) == groupId) {
                    GroupPublicJoinResult.Joined
                } else {
                    GroupPublicJoinResult.OutcomeUnknown
                }
            }

            is ApiResult.Failure -> {
                when (val failure = result.reason) {
                    is NetworkFailure.HttpStatus -> {
                        when {
                            failure.statusCode == 409 && failure.error?.code == "GROUP-007" -> {
                                GroupPublicJoinResult.AlreadyMember
                            }

                            failure.statusCode == 409 && failure.error?.code == "GROUP-009" -> {
                                GroupPublicJoinResult.PrivateGroup
                            }

                            failure.statusCode == 404 && failure.error?.code == "GROUP-001" -> {
                                GroupPublicJoinResult.NotFound
                            }

                            failure.statusCode >= 500 || failure.statusCode == 408 || failure.statusCode == 425 -> {
                                GroupPublicJoinResult.OutcomeUnknown
                            }

                            else -> {
                                GroupPublicJoinResult.Unavailable
                            }
                        }
                    }

                    is NetworkFailure.Transport -> {
                        uncertain(failure.mutationCertainty)
                    }

                    is NetworkFailure.Unexpected -> {
                        uncertain(failure.mutationCertainty)
                    }

                    is NetworkFailure.Contract -> {
                        uncertain(failure.mutationCertainty)
                    }

                    is NetworkFailure.SessionUnavailable, is NetworkFailure.SessionProviderFailed -> {
                        GroupPublicJoinResult.Unavailable
                    }
                }
            }
        }

    private fun uncertain(certainty: MutationCertainty) =
        if (certainty ==
            MutationCertainty.UNKNOWN
        ) {
            GroupPublicJoinResult.OutcomeUnknown
        } else {
            GroupPublicJoinResult.Unavailable
        }
}
