package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.ContractFailureReason
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.group.GroupJoinApi
import com.pheeeew.data.remote.group.GroupResponseMapper
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.repository.GroupInviteLookupResult
import com.pheeeew.domain.repository.GroupJoinRepository
import com.pheeeew.domain.repository.GroupJoinRepositoryResult
import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

class GroupJoinRepositoryImpl(
    private val api: GroupJoinApi,
) : GroupJoinRepository {
    override suspend fun findByInviteCode(normalizedCode: String): GroupInviteLookupResult =
        when (val result = api.findByInviteCode(normalizedCode)) {
            is ApiResult.Success -> {
                try {
                    GroupInviteLookupResult.Found(GroupResponseMapper.toDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupInviteLookupResult.Unavailable
                }
            }

            is ApiResult.Failure -> result.reason.toLookupResult()
        }

    override suspend fun join(normalizedCode: String): GroupJoinRepositoryResult =
        when (val result = api.join(normalizedCode)) {
            is ApiResult.Success -> {
                GroupId.parse(result.value.groupId)?.let(GroupJoinRepositoryResult::Joined)
                    ?: GroupJoinRepositoryResult.OutcomeUnknown
            }

            is ApiResult.Failure -> result.reason.toJoinResult()
        }

    private fun NetworkFailure.toLookupResult(): GroupInviteLookupResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when (statusCode) {
                    NOT_FOUND_STATUS -> GroupInviteLookupResult.NotFound
                    RATE_LIMIT_STATUS -> GroupInviteLookupResult.RateLimited(retryAfter.toDelayMillis())
                    else -> GroupInviteLookupResult.Unavailable
                }
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            is NetworkFailure.Transport,
            is NetworkFailure.Unexpected,
            is NetworkFailure.Contract,
            -> GroupInviteLookupResult.Unavailable
        }

    private fun NetworkFailure.toJoinResult(): GroupJoinRepositoryResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when {
                    statusCode == NOT_FOUND_STATUS -> GroupJoinRepositoryResult.InviteCodeNotFound
                    statusCode == ALREADY_MEMBER_STATUS -> GroupJoinRepositoryResult.AlreadyMember
                    statusCode == RATE_LIMIT_STATUS -> GroupJoinRepositoryResult.RateLimited(retryAfter.toDelayMillis())
                    statusCode == UNAUTHORIZED_STATUS -> GroupJoinRepositoryResult.Unavailable
                    statusCode == REQUEST_TIMEOUT_STATUS || statusCode == TOO_EARLY_STATUS || statusCode >= 500 -> {
                        GroupJoinRepositoryResult.OutcomeUnknown
                    }

                    statusCode in CLIENT_ERROR_RANGE -> GroupJoinRepositoryResult.Rejected
                    else -> mutationCertainty.toJoinFailure()
                }
            }

            is NetworkFailure.Transport -> mutationCertainty.toJoinFailure()
            is NetworkFailure.Unexpected -> mutationCertainty.toJoinFailure()

            is NetworkFailure.Contract -> {
                if (reason == ContractFailureReason.MALFORMED_REQUEST_BODY) {
                    GroupJoinRepositoryResult.Unavailable
                } else {
                    mutationCertainty.toJoinFailure()
                }
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            -> GroupJoinRepositoryResult.Unavailable
        }

    private fun MutationCertainty.toJoinFailure(): GroupJoinRepositoryResult =
        if (this == MutationCertainty.UNKNOWN) {
            GroupJoinRepositoryResult.OutcomeUnknown
        } else {
            GroupJoinRepositoryResult.Unavailable
        }

    private fun String?.toDelayMillis(): Long? {
        val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val retryAfterSeconds = value.toLongOrNull()
        if (retryAfterSeconds != null) {
            if (retryAfterSeconds < 0L) return null
            if (retryAfterSeconds > Long.MAX_VALUE / MILLIS_PER_SECOND) return Long.MAX_VALUE
            return retryAfterSeconds * MILLIS_PER_SECOND
        }

        val retryAtMillis = runCatching { value.fromHttpToGmtDate().timestamp }.getOrNull() ?: return null
        return (retryAtMillis - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
    }

    private companion object {
        const val NOT_FOUND_STATUS = 404
        const val ALREADY_MEMBER_STATUS = 409
        const val RATE_LIMIT_STATUS = 429
        const val UNAUTHORIZED_STATUS = 401
        const val REQUEST_TIMEOUT_STATUS = 408
        const val TOO_EARLY_STATUS = 425
        const val MILLIS_PER_SECOND = 1_000L
        val CLIENT_ERROR_RANGE = 400..499
    }
}
