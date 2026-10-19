package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.GroupStampItemResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
class GroupStampListApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findMyStamps(): ApiResult<List<GroupStampItemResponseDto>> =
        requests.execute(
            ApiRequest(HttpMethod.Get, "/api/v2/groups/stamps", RequestKind.READ, monitoringEndpoint = "group_stamps"),
        ) { response ->
            response.body<List<GroupStampItemResponseDto>>()
        }
}
