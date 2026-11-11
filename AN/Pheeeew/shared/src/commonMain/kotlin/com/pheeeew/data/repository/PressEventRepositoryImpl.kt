package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.api.PressEventApi
import com.pheeeew.data.remote.dto.PressEventAttemptRequestDto
import com.pheeeew.data.remote.mapper.PressEventMapper
import com.pheeeew.domain.model.pressevent.PressEventAttempt
import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.domain.model.pressevent.PressEventRankingPage
import com.pheeeew.domain.repository.PressEventFailure
import com.pheeeew.domain.repository.PressEventRepository
import com.pheeeew.domain.repository.PressEventResult
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

@Inject
internal class PressEventRepositoryImpl(
    private val api: PressEventApi,
) : PressEventRepository {
    override suspend fun submit(
        eventId: String,
        attempt: PressEventAttempt,
        sessionGeneration: Long,
    ): PressEventResult<PressEventAttemptResult> =
        map(
            api.submit(
                eventId,
                PressEventAttemptRequestDto(
                    attempt.requestId,
                    attempt.counts.mapKeys {
                        it.key.name
                    },
                ),
                sessionGeneration,
            ),
        ) {
            PressEventMapper.attempt(it, attempt)
        }

    override suspend fun rankings(
        eventId: String,
        sessionGeneration: Long,
        limit: Int,
        cursor: String?,
    ): PressEventResult<PressEventRankingPage> =
        map(api.rankings(eventId, sessionGeneration, limit, cursor)) { dto ->
            val page = PressEventMapper.rankings(dto)
            require(!page.hasNext || page.nextCursor != cursor) { "순위 cursor가 진행되지 않았습니다." }
            page
        }

    private fun <T, R> map(
        result: ApiResult<T>,
        transform: (T) -> R,
    ): PressEventResult<R> =
        when (result) {
            is ApiResult.Success -> {
                try {
                    PressEventResult.Success(transform(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IllegalArgumentException) {
                    PressEventResult.Failure(PressEventFailure.Retryable)
                }
            }

            is ApiResult.Failure -> {
                PressEventResult.Failure(
                    when (val failure = result.reason) {
                        is NetworkFailure.SessionUnavailable, is NetworkFailure.SessionProviderFailed -> {
                            PressEventFailure.SessionChanged
                        }

                        is NetworkFailure.HttpStatus -> {
                            when (failure.statusCode) {
                                409 -> PressEventFailure.Conflict
                                408, 429 -> PressEventFailure.Retryable
                                in 500..599 -> PressEventFailure.Retryable
                                else -> PressEventFailure.Rejected
                            }
                        }

                        else -> {
                            PressEventFailure.Retryable
                        }
                    },
                )
            }
        }
}
