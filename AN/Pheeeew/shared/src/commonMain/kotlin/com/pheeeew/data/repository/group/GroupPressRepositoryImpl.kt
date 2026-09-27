package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.ContractFailureReason
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.group.api.GroupPressApi
import com.pheeeew.data.remote.group.mapper.GroupPressCountResponseMapper
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupPressState
import com.pheeeew.domain.repository.group.GroupPressRepository
import com.pheeeew.domain.repository.group.GroupPressResult
import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

class GroupPressRepositoryImpl(
    private val api: GroupPressApi,
) : GroupPressRepository {
    override suspend fun press(
        groupId: GroupId,
        state: GroupPressState,
    ): GroupPressResult =
        when (val result = api.press(groupId.value, state.name)) {
            is ApiResult.Success -> {
                try {
                    GroupPressResult.Pressed(GroupPressCountResponseMapper.toDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A malformed 200 can still mean that the write was applied.
                    GroupPressResult.OutcomeUnknown
                }
            }

            is ApiResult.Failure -> {
                result.reason.toPressResult()
            }
        }

    private fun NetworkFailure.toPressResult(): GroupPressResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when {
                    statusCode == FORBIDDEN_STATUS -> {
                        GroupPressResult.MembershipChanged
                    }

                    statusCode == NOT_FOUND_STATUS -> {
                        GroupPressResult.NotFound
                    }

                    statusCode == RATE_LIMIT_STATUS -> {
                        GroupPressResult.RateLimited(retryAfter.toDelayMillis())
                    }

                    statusCode == REQUEST_TIMEOUT_STATUS || statusCode == TOO_EARLY_STATUS || statusCode >= 500 -> {
                        GroupPressResult.OutcomeUnknown
                    }

                    statusCode == UNAUTHORIZED_STATUS -> {
                        GroupPressResult.Unavailable
                    }

                    statusCode in CLIENT_ERROR_RANGE -> {
                        GroupPressResult.Rejected
                    }

                    else -> {
                        mutationCertainty.toPressFailure()
                    }
                }
            }

            is NetworkFailure.Transport -> {
                mutationCertainty.toPressFailure()
            }

            is NetworkFailure.Unexpected -> {
                mutationCertainty.toPressFailure()
            }

            is NetworkFailure.Contract -> {
                if (reason == ContractFailureReason.MALFORMED_REQUEST_BODY) {
                    GroupPressResult.Unavailable
                } else {
                    mutationCertainty.toPressFailure()
                }
            }

            is NetworkFailure.SessionUnavailable -> {
                GroupPressResult.Unavailable
            }

            is NetworkFailure.SessionProviderFailed -> {
                GroupPressResult.Unavailable
            }
        }

    private fun MutationCertainty.toPressFailure(): GroupPressResult =
        if (this == MutationCertainty.UNKNOWN) GroupPressResult.OutcomeUnknown else GroupPressResult.Unavailable

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
        const val FORBIDDEN_STATUS = 403
        const val NOT_FOUND_STATUS = 404
        const val RATE_LIMIT_STATUS = 429
        const val UNAUTHORIZED_STATUS = 401
        const val REQUEST_TIMEOUT_STATUS = 408
        const val TOO_EARLY_STATUS = 425
        const val MILLIS_PER_SECOND = 1_000L
        val CLIENT_ERROR_RANGE = 400..499
    }
}
