package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupDetailResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

/** Reads a group's current detail and issues the body's documented bodyless leave operation. */
class GroupDetailApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findById(groupId: String): ApiResult<GroupDetailResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$GROUPS_PATH/$groupId",
                kind = RequestKind.READ,
            ),
        ) { response -> response.body<GroupDetailResponseDto>() }

    suspend fun leave(groupId: String): ApiResult<Unit> =
        requests.executeNoContent(
            ApiRequest(
                method = HttpMethod.Delete,
                path = "$GROUPS_PATH/$groupId/members/me",
                kind = RequestKind.WRITE,
                replayAfterAuthentication = false,
            ),
        )

    private companion object {
        const val GROUPS_PATH = "/api/v2/groups"
    }
}
