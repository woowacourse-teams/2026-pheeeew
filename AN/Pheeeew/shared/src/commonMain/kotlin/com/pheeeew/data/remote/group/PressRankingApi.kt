package com.pheeeew.data.remote.group

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class PressRankingApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun find(
        weeksAgo: Int,
        state: String?,
    ): ApiResult<PressRankingResponseDto> {
        require(weeksAgo >= 0) { "weeksAgo는 0 이상이어야 합니다." }
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

    private companion object {
        const val PATH = "/api/v2/groups/press-rankings"
        val STATES = setOf("FRUSTRATED", "IRRITATED", "EXHAUSTED", "DISCOURAGED", "ANGRY")
    }
}
