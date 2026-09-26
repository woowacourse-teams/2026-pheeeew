package com.pheeeew.data.remote.group

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class GroupCreateApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun create(request: GroupCreateRequestDto): ApiResult<GroupCreateResponseDto> =
        requests.execute(
            ApiRequest(HttpMethod.Post, PATH, RequestKind.WRITE, body = request),
        ) { response -> response.body<GroupCreateResponseDto>() }

    private companion object {
        const val PATH = "/api/v2/groups"
    }
}
