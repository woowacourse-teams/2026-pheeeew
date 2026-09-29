package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupCreateRequestDto
import com.pheeeew.data.remote.group.dto.GroupCreateResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class GroupCreateApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun create(request: GroupCreateRequestDto): ApiResult<GroupCreateResponseDto> =
        requests.execute(
            ApiRequest(HttpMethod.Post, PATH, RequestKind.WRITE, body = request, monitoringEndpoint = "group_create"),
        ) { response -> response.body<GroupCreateResponseDto>() }

    private companion object {
        const val PATH = "/api/v2/groups"
    }
}
