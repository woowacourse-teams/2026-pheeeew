package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupStampItemResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

/** Reads only the authenticated device's current group IDs, names, and stamps. */
class GroupStampListApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findMine() =
        requests.execute(ApiRequest(HttpMethod.Get, "/api/v2/groups/stamps", RequestKind.READ)) { response ->
            response.body<List<GroupStampItemResponseDto>>()
        }
}
