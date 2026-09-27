package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupJoinRequestDto
import com.pheeeew.data.remote.group.dto.GroupJoinResponseDto
import com.pheeeew.data.remote.group.dto.GroupPreviewResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

/** Invitation-code lookup and membership endpoints. */
class GroupJoinApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findByInviteCode(normalizedCode: String): ApiResult<GroupPreviewResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = SEARCH_PATH,
                kind = RequestKind.READ,
                queryParameters = mapOf(INVITE_CODE_PARAMETER to normalizedCode),
            ),
        ) { response -> response.body<GroupPreviewResponseDto>() }

    suspend fun join(normalizedCode: String): ApiResult<GroupJoinResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = JOIN_PATH,
                kind = RequestKind.WRITE,
                body = GroupJoinRequestDto(normalizedCode),
                // AUTH-001 is rejected before the handler; replaying this membership operation is safe.
                replayAfterAuthentication = true,
            ),
        ) { response -> response.body<GroupJoinResponseDto>() }

    private companion object {
        const val SEARCH_PATH = "/api/v2/groups/search"
        const val JOIN_PATH = "/api/v2/groups/join"
        const val INVITE_CODE_PARAMETER = "inviteCode"
    }
}
