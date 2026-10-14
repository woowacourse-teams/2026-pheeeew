package com.pheeeew.data.remote.press

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.MyDailyPressTotals
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.repository.press.PressSender
import kotlinx.coroutines.CancellationException

/** Adapts the session batch to the position-free counts-only API contract. */
internal class PressApiSender(
    private val api: EmotionPressApi,
) : PressSender {
    override suspend fun send(batch: PressBatch): PressSendResult =
        when (val result = api.press(batch.counts.mapKeys { (emotion, _) -> emotion.name })) {
            is ApiResult.Success -> {
                try {
                    PressSendResult.Accepted(result.value.toDomainTotals())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A 200 write with an unusable body may already be committed; never replay it blindly.
                    PressSendResult.OutcomeUnknown
                }
            }

            is ApiResult.Failure -> {
                result.reason.toSendResult()
            }
        }

    private fun EmotionPressWriteResponseDto.toDomainTotals(): MyDailyPressTotals {
        val expectedKeys = EmotionState.entries.map { it.name }.toSet()
        require(counts.keys == expectedKeys)
        val domainCounts = EmotionState.entries.associateWith { emotion -> counts.getValue(emotion.name) }
        return MyDailyPressTotals(domainCounts, total)
    }

    private fun NetworkFailure.toSendResult(): PressSendResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when {
                    statusCode == BAD_REQUEST -> PressSendResult.Rejected
                    statusCode == UNAUTHORIZED -> PressSendResult.NotSent
                    mutationCertainty == MutationCertainty.NOT_APPLIED -> PressSendResult.NotSent
                    mutationCertainty == MutationCertainty.UNKNOWN -> PressSendResult.OutcomeUnknown
                    else -> PressSendResult.NotSent
                }
            }

            is NetworkFailure.Contract -> {
                when (mutationCertainty) {
                    MutationCertainty.UNKNOWN -> PressSendResult.OutcomeUnknown
                    MutationCertainty.NOT_APPLIED -> PressSendResult.Rejected
                    MutationCertainty.NOT_A_MUTATION -> PressSendResult.NotSent
                }
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            -> {
                PressSendResult.NotSent
            }

            is NetworkFailure.Transport -> {
                if (mutationCertainty == MutationCertainty.UNKNOWN) {
                    PressSendResult.OutcomeUnknown
                } else {
                    PressSendResult.NotSent
                }
            }

            is NetworkFailure.Unexpected -> {
                if (mutationCertainty == MutationCertainty.UNKNOWN) {
                    PressSendResult.OutcomeUnknown
                } else {
                    PressSendResult.NotSent
                }
            }
        }

    private companion object {
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
    }
}
