package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupPressCountResponseDto
import com.pheeeew.data.remote.group.dto.GroupPressRequestDto
import com.pheeeew.domain.model.group.GroupPressState
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class GroupPressApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findWeekly(groupId: String): ApiResult<GroupPressCountResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$GROUPS_PATH/$groupId/presses",
                kind = RequestKind.READ,
                monitoringEndpoint = "group_weekly_presses",
            ),
        ) { response -> response.body<GroupPressCountResponseDto>() }

    suspend fun press(
        groupId: String,
        state: GroupPressState,
    ): ApiResult<GroupPressCountResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "$GROUPS_PATH/$groupId/presses",
                kind = RequestKind.WRITE,
                body = GroupPressRequestDto(state = state.name),
                // Only AUTH-001, which is rejected before the handler, may trigger the executor's safe refresh.
                // Timeouts and every other uncertain result are never replayed.
                replayAfterAuthentication = true,
                monitoringEndpoint = "group_press",
            ),
        ) { response -> response.body<GroupPressCountResponseDto>() }

    private companion object {
        const val GROUPS_PATH = "/api/v2/groups"
    }
}
