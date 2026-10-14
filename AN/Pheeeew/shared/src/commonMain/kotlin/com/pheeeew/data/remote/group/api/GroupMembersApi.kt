package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupMembersResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class GroupMembersApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun loadPage(
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
        // Keep nextCursor required even when null; the app client permits omitted nullable fields.
        val responseJson = Json { ignoreUnknownKeys = true }
    }
}
