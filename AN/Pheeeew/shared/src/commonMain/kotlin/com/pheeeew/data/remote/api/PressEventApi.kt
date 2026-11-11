package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.PressEventAttemptRequestDto
import com.pheeeew.data.remote.dto.PressEventAttemptResponseDto
import com.pheeeew.data.remote.dto.PressEventRankingResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart

@Inject
internal class PressEventApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun submit(
        eventId: String,
        body: PressEventAttemptRequestDto,
        sessionGeneration: Long,
    ): ApiResult<PressEventAttemptResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "${path(eventId)}/attempts",
                kind = RequestKind.WRITE,
                body = body,
                replayAfterAuthentication = true,
                expectedSessionGeneration = sessionGeneration,
                monitoringEndpoint = "press_event_attempt",
            ),
        ) { it.body<PressEventAttemptResponseDto>() }

    suspend fun rankings(
        eventId: String,
        sessionGeneration: Long,
        limit: Int,
        cursor: String?,
    ): ApiResult<PressEventRankingResponseDto> {
        require(limit in 1..100)
        require(cursor == null || cursor.isNotBlank())
        val query =
            buildMap {
                put("limit", limit.toString())
                if (cursor != null) put("cursor", cursor)
            }
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "${path(eventId)}/rankings",
                kind = RequestKind.READ,
                queryParameters = query,
                expectedSessionGeneration = sessionGeneration,
                monitoringEndpoint = "press_event_rankings",
            ),
        ) { it.body<PressEventRankingResponseDto>() }
    }

    private fun path(eventId: String): String {
        require(eventId.isNotBlank())
        return "/api/v1/press-events/${eventId.encodeURLPathPart()}"
    }
}
