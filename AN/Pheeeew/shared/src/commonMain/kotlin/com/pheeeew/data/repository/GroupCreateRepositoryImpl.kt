package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.ContractFailureReason
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.group.GroupCreateApi
import com.pheeeew.data.remote.group.GroupCreateRequestMapper
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.repository.GroupCreateCommand
import com.pheeeew.domain.repository.GroupCreateRepository
import com.pheeeew.domain.repository.GroupCreateRepositoryResult

class GroupCreateRepositoryImpl(
    private val api: GroupCreateApi,
) : GroupCreateRepository {
    override suspend fun create(command: GroupCreateCommand): GroupCreateRepositoryResult =
        when (val result = api.create(GroupCreateRequestMapper.toRequestDto(command))) {
            is ApiResult.Success -> {
                GroupId.parse(result.value.groupId)?.let(GroupCreateRepositoryResult::Created)
                    ?: GroupCreateRepositoryResult.OutcomeUnknown
            }

            is ApiResult.Failure -> {
                result.reason.toCreateResult()
            }
        }

    private fun NetworkFailure.toCreateResult(): GroupCreateRepositoryResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when {
                    statusCode == DUPLICATE_NAME_STATUS -> {
                        GroupCreateRepositoryResult.DuplicateName
                    }

                    statusCode == INVALID_INPUT_STATUS -> {
                        GroupCreateRepositoryResult.InvalidInput
                    }

                    statusCode == RATE_LIMIT_STATUS -> {
                        GroupCreateRepositoryResult.RateLimited
                    }

                    statusCode == REQUEST_TIMEOUT_STATUS || statusCode == TOO_EARLY_STATUS || statusCode >= 500 -> {
                        GroupCreateRepositoryResult.OutcomeUnknown
                    }

                    else -> {
                        GroupCreateRepositoryResult.Unavailable
                    }
                }
            }

            is NetworkFailure.Transport -> {
                if (mutationCertainty == MutationCertainty.UNKNOWN) {
                    GroupCreateRepositoryResult.OutcomeUnknown
                } else {
                    GroupCreateRepositoryResult.Unavailable
                }
            }

            is NetworkFailure.Unexpected -> {
                if (mutationCertainty == MutationCertainty.UNKNOWN) {
                    GroupCreateRepositoryResult.OutcomeUnknown
                } else {
                    GroupCreateRepositoryResult.Unavailable
                }
            }

            is NetworkFailure.Contract -> {
                if (mutationCertainty == MutationCertainty.UNKNOWN &&
                    reason != ContractFailureReason.MALFORMED_REQUEST_BODY
                ) {
                    GroupCreateRepositoryResult.OutcomeUnknown
                } else {
                    GroupCreateRepositoryResult.Unavailable
                }
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            -> {
                GroupCreateRepositoryResult.Unavailable
            }
        }

    private companion object {
        const val INVALID_INPUT_STATUS = 400
        const val REQUEST_TIMEOUT_STATUS = 408
        const val DUPLICATE_NAME_STATUS = 409
        const val RATE_LIMIT_STATUS = 429
        const val TOO_EARLY_STATUS = 425
    }
}
