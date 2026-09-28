package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupStampItemResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class GroupStampListApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findMyStamps(): ApiResult<List<GroupStampItemResponseDto>> =
        requests.execute(ApiRequest(HttpMethod.Get, "/api/v2/groups/stamps", RequestKind.READ)) { response ->
            response.body<List<GroupStampItemResponseDto>>()
        }
}
