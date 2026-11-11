package com.pheeeew.domain.repository

import com.pheeeew.domain.model.pressevent.PressEventAttempt
import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.domain.model.pressevent.PressEventRankingPage

internal sealed interface PressEventResult<out T> {
    data class Success<T>(
        val value: T,
    ) : PressEventResult<T>

    data class Failure(
        val reason: PressEventFailure,
    ) : PressEventResult<Nothing>
}

internal enum class PressEventFailure { Retryable, Conflict, Rejected, SessionChanged }

internal interface PressEventRepository {
    suspend fun submit(
        eventId: String,
        attempt: PressEventAttempt,
        sessionGeneration: Long,
    ): PressEventResult<PressEventAttemptResult>

    suspend fun rankings(
        eventId: String,
        sessionGeneration: Long,
        limit: Int = 20,
        cursor: String? = null,
    ): PressEventResult<PressEventRankingPage>
}
