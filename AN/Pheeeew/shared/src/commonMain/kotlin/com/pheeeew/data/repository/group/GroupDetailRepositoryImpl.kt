package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.ContractFailureReason
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.group.api.GroupDetailApi
import com.pheeeew.data.remote.group.mapper.GroupDetailResponseMapper
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.repository.group.GroupDetailLookupResult
import com.pheeeew.domain.repository.group.GroupDetailRepository
import com.pheeeew.domain.repository.group.GroupLeaveResult
import kotlinx.coroutines.CancellationException

class GroupDetailRepositoryImpl(
    private val api: GroupDetailApi,
) : GroupDetailRepository {
    override suspend fun findById(groupId: GroupId): GroupDetailLookupResult =
        when (val result = api.findById(groupId.value)) {
            is ApiResult.Success -> {
                try {
                    val detail = GroupDetailResponseMapper.toDomain(result.value)
                    if (!detail.group.id.value
                            .equals(groupId.value, ignoreCase = true)
                    ) {
                        GroupDetailLookupResult.Unavailable
                    } else {
                        GroupDetailLookupResult.Found(detail)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupDetailLookupResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                result.reason.toLookupResult()
            }
        }

    override suspend fun leave(groupId: GroupId): GroupLeaveResult =
        when (val result = api.leave(groupId.value)) {
            is ApiResult.Success -> GroupLeaveResult.Left
            is ApiResult.Failure -> result.reason.toLeaveResult()
        }

    private fun NetworkFailure.toLookupResult(): GroupDetailLookupResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when (statusCode) {
                    FORBIDDEN_STATUS -> GroupDetailLookupResult.MembershipChanged
                    NOT_FOUND_STATUS -> GroupDetailLookupResult.NotFound
                    else -> GroupDetailLookupResult.Unavailable
                }
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            is NetworkFailure.Transport,
            is NetworkFailure.Unexpected,
            is NetworkFailure.Contract,
            -> {
                GroupDetailLookupResult.Unavailable
            }
        }

    private fun NetworkFailure.toLeaveResult(): GroupLeaveResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when {
                    statusCode == FORBIDDEN_STATUS -> {
                        GroupLeaveResult.MembershipChanged
                    }

                    statusCode == NOT_FOUND_STATUS -> {
                        GroupLeaveResult.NotFound
                    }

                    statusCode == CONFLICT_STATUS -> {
                        GroupLeaveResult.OwnerCannotLeave
                    }

                    statusCode == REQUEST_TIMEOUT_STATUS || statusCode == TOO_EARLY_STATUS || statusCode >= 500 -> {
                        GroupLeaveResult.OutcomeUnknown
                    }

                    else -> {
                        GroupLeaveResult.Unavailable
                    }
                }
            }

            is NetworkFailure.Transport -> {
                mutationCertainty.toLeaveFailure()
            }

            is NetworkFailure.Unexpected -> {
                mutationCertainty.toLeaveFailure()
            }

            is NetworkFailure.Contract -> {
                if (reason == ContractFailureReason.MALFORMED_REQUEST_BODY) {
                    GroupLeaveResult.Unavailable
                } else {
                    mutationCertainty.toLeaveFailure()
                }
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            -> {
                GroupLeaveResult.Unavailable
            }
        }

    private fun MutationCertainty.toLeaveFailure(): GroupLeaveResult =
        if (this == MutationCertainty.UNKNOWN) GroupLeaveResult.OutcomeUnknown else GroupLeaveResult.Unavailable

    private companion object {
        const val FORBIDDEN_STATUS = 403
        const val NOT_FOUND_STATUS = 404
        const val CONFLICT_STATUS = 409
        const val REQUEST_TIMEOUT_STATUS = 408
        const val TOO_EARLY_STATUS = 425
    }
}
