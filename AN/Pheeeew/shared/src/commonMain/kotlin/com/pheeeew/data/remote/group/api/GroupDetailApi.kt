package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupDetailResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class GroupDetailApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findById(groupId: String): ApiResult<GroupDetailResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$DETAIL_PATH/$groupId",
                kind = RequestKind.READ,
                monitoringEndpoint = "group_detail",
            ),
        ) { response -> response.body<GroupDetailResponseDto>() }

    suspend fun leave(groupId: String): ApiResult<Unit> =
        requests.executeNoContent(
            ApiRequest(
                method = HttpMethod.Delete,
                path = "$LEAVE_PATH/$groupId/members/me",
                kind = RequestKind.WRITE,
                replayAfterAuthentication = false,
                monitoringEndpoint = "group_leave",
            ),
        )

    private companion object {
        const val DETAIL_PATH = "/api/v3/groups"
        const val LEAVE_PATH = "/api/v2/groups"
    }
}
