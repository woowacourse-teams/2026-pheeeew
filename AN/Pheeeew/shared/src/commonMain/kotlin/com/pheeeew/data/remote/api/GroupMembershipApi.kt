package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.GroupJoinRequestDto
import com.pheeeew.data.remote.dto.GroupJoinResponseDto
import com.pheeeew.data.remote.dto.GroupMembersResponseDto
import com.pheeeew.data.remote.dto.GroupPreviewResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Inject
class GroupMembershipApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findByInviteCode(normalizedCode: String): ApiResult<GroupPreviewResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = SEARCH_PATH,
                kind = RequestKind.READ,
                queryParameters = mapOf(INVITE_CODE_PARAMETER to normalizedCode),
                monitoringEndpoint = "group_lookup",
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
                monitoringEndpoint = "group_join",
            ),
        ) { response -> response.body<GroupJoinResponseDto>() }

    suspend fun joinPublic(groupId: String): ApiResult<GroupJoinResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "/api/v2/groups/$groupId/members/me",
                kind = RequestKind.WRITE,
                body = null,
                replayAfterAuthentication = false,
                monitoringEndpoint = "group_public_join",
            ),
        ) { response -> response.body<GroupJoinResponseDto>() }

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

    suspend fun members(
        groupId: String,
        cursor: String?,
    ): ApiResult<GroupMembersResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "/api/v2/groups/$groupId/members",
                kind = RequestKind.READ,
                queryParameters =
                    buildMap {
                        put("size", "20")
                        cursor?.let { put("cursor", it) }
                    },
                monitoringEndpoint = "group_members",
            ),
        ) { response -> responseJson.decodeFromJsonElement<GroupMembersResponseDto>(response.body<JsonObject>()) }

    private companion object {
        const val SEARCH_PATH = "/api/v2/groups/search"
        const val JOIN_PATH = "/api/v2/groups/join"
        const val INVITE_CODE_PARAMETER = "inviteCode"
        const val LEAVE_PATH = "/api/v2/groups"
        val responseJson = Json { ignoreUnknownKeys = true }
    }
}
