package com.pheeeew.data.remote.group

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class GroupRankingApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun find(weeksAgo: Int): ApiResult<GroupRankingResponseDto> {
        require(weeksAgo >= 0) { "weeksAgo는 0 이상이어야 합니다." }
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = PATH,
                kind = RequestKind.READ,
                queryParameters = mapOf("weeksAgo" to weeksAgo.toString()),
            ),
        ) { response -> response.body<GroupRankingResponseDto>() }
    }

    private companion object {
        const val PATH = "/api/v2/groups/rankings"
    }
}
