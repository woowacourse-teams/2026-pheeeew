package com.pheeeew.data.repository.press

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.ContractFailureReason
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.press.EmotionPressApi
import com.pheeeew.data.remote.press.EmotionPressResponseDto
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressRepository
import com.pheeeew.domain.repository.press.PressSubmitResult
import kotlinx.coroutines.CancellationException

internal class PressRepositoryImpl(
    private val api: EmotionPressApi,
) : PressRepository {
    override suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot> =
        when (val response = api.findMyToday()) {
            is ApiResult.Success -> {
                safelyRead {
                    val counts = response.value.counts.toDomainCounts()
                    MyDailyPressSnapshot(
                        pressDate = response.value.pressDate,
                        counts = counts,
                        total = response.value.total,
                    )
                }
            }

            is ApiResult.Failure -> {
                PressReadResult.Unavailable
            }
        }

    override suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot> =
        when (val response = api.findAllToday()) {
            is ApiResult.Success -> {
                safelyRead {
                    AllDailyPressSnapshot(response.value.pressDate, response.value.total)
                }
            }

            is ApiResult.Failure -> {
                PressReadResult.Unavailable
            }
        }

    override suspend fun submit(
        location: CurrentLocation,
        counts: Map<EmotionState, Int>,
    ): PressSubmitResult =
        when (val response = api.submit(location, counts)) {
            is ApiResult.Success -> {
                try {
                    response.value.validate()
                    PressSubmitResult.Submitted
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A malformed success response may follow a committed write.
                    PressSubmitResult.OutcomeUnknown
                }
            }

            is ApiResult.Failure -> {
                response.reason.toSubmitResult()
            }
        }

    private suspend fun <T> safelyRead(block: () -> T): PressReadResult<T> =
        try {
            PressReadResult.Loaded(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PressReadResult.Unavailable
        }

    private fun NetworkFailure.toSubmitResult(): PressSubmitResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when {
                    statusCode == REQUEST_TIMEOUT_STATUS || statusCode == TOO_EARLY_STATUS || statusCode >= 500 -> {
                        PressSubmitResult.OutcomeUnknown
                    }

                    statusCode in CLIENT_ERROR_RANGE -> {
                        PressSubmitResult.Rejected
                    }

                    else -> {
                        mutationCertainty.toSubmitResult()
                    }
                }
            }

            is NetworkFailure.Transport -> {
                mutationCertainty.toSubmitResult()
            }

            is NetworkFailure.Unexpected -> {
                mutationCertainty.toSubmitResult()
            }

            is NetworkFailure.Contract -> {
                if (reason == ContractFailureReason.MALFORMED_REQUEST_BODY) {
                    PressSubmitResult.Unavailable
                } else {
                    mutationCertainty.toSubmitResult()
                }
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            -> {
                PressSubmitResult.Unavailable
            }
        }

    private fun MutationCertainty.toSubmitResult(): PressSubmitResult =
        if (this == MutationCertainty.UNKNOWN) PressSubmitResult.OutcomeUnknown else PressSubmitResult.Unavailable

    private fun EmotionPressResponseDto.validate() {
        require(regionCode.isNotBlank())
        require(total >= 0L)
        val values = counts.toDomainCounts()
        require(values.values.sum() == total)
    }

    private fun Map<String, Long>.toDomainCounts(): Map<EmotionState, Long> {
        val valuesByKey = this
        return EmotionState.entries
            .associateWith { emotion ->
                val value = valuesByKey[emotion.name] ?: error("누락된 감정 집계입니다.")
                require(value >= 0L)
                value
            }.also { require(valuesByKey.keys == EmotionState.entries.map { it.name }.toSet()) }
    }

    private companion object {
        const val REQUEST_TIMEOUT_STATUS = 408
        const val TOO_EARLY_STATUS = 425
        val CLIENT_ERROR_RANGE = 400..499
    }
}
