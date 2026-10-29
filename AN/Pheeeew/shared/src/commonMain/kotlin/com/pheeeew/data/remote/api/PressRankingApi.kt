package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.DailyPressRankingResponseDto
import com.pheeeew.data.remote.dto.PressRankingResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
class PressRankingApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun find(
        weeksAgo: Int,
        state: String?,
    ): ApiResult<PressRankingResponseDto> {
        require(weeksAgo in 0..520) { "weeksAgo는 0 이상 520 이하여야 합니다." }
        require(state == null || state in STATES) { "지원하지 않는 감정 상태입니다." }

        val path = if (state == null) PATH else "$PATH/states/$state"
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = path,
                kind = RequestKind.READ,
                // The server resolves the current device from the bearer token.
                queryParameters = mapOf("weeksAgo" to weeksAgo.toString()),
                monitoringEndpoint = if (state == null) "press_ranking" else "press_ranking_by_state",
            ),
        ) { response -> response.body<PressRankingResponseDto>() }
    }

    suspend fun findDaily(
        date: String,
        expectedSessionGeneration: Long? = null,
    ): ApiResult<DailyPressRankingResponseDto> {
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date)) { "date는 yyyy-MM-dd 형식이어야 합니다." }
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = DAILY_PATH,
                kind = RequestKind.READ,
                queryParameters = mapOf("date" to date),
                monitoringEndpoint = "daily_press_ranking",
                expectedSessionGeneration = expectedSessionGeneration,
            ),
        ) { response -> response.body<DailyPressRankingResponseDto>() }
    }

    private companion object {
        const val PATH = "/api/v2/emotions/press-rankings"
        const val DAILY_PATH = "/api/v2/emotions/press-rankings/daily"
        val STATES = setOf("FRUSTRATED", "IRRITATED", "EXHAUSTED", "DISCOURAGED", "ANGRY")
    }
}
