package com.pheeeew.data.remote.group

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

/** Reads the authenticated device's groups in the order returned by the server. */
class GroupListApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findMine(): ApiResult<List<GroupResponseDto>> =
        requests.execute(ApiRequest(HttpMethod.Get, PATH, RequestKind.READ)) { response ->
            response.body<List<GroupResponseDto>>()
        }

    private companion object {
        const val PATH = "/api/v2/groups"
    }
}
