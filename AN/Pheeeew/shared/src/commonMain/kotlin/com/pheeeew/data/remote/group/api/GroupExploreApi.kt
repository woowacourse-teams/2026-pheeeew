package com.pheeeew.data.remote.group.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.group.dto.GroupExploreResponseDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class GroupExploreApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): ApiResult<GroupExploreResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "/api/v2/groups/explore",
                kind = RequestKind.READ,
                queryParameters =
                    buildMap {
                        put("size", "20")
                        keyword?.trim()?.takeIf(String::isNotEmpty)?.let { put("keyword", it) }
                        cursor?.let { put("cursor", it) }
                    },
                monitoringEndpoint = "group_explore",
            ),
        ) { response -> responseJson.decodeFromJsonElement<GroupExploreResponseDto>(response.body<JsonObject>()) }

    private companion object {
        // Keep nextCursor required even when null; the app client permits omitted nullable fields.
        val responseJson = Json { ignoreUnknownKeys = true }
    }
}
