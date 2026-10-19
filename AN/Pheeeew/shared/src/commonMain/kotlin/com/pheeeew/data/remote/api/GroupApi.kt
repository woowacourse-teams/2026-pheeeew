package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.GroupCreateRequestDto
import com.pheeeew.data.remote.dto.GroupCreateResponseDto
import com.pheeeew.data.remote.dto.GroupDetailResponseDto
import com.pheeeew.data.remote.dto.GroupExploreResponseDto
import com.pheeeew.data.remote.dto.GroupResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Inject
class GroupApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun create(request: GroupCreateRequestDto): ApiResult<GroupCreateResponseDto> =
        requests.execute(
            ApiRequest(HttpMethod.Post, PATH, RequestKind.WRITE, body = request, monitoringEndpoint = "group_create"),
        ) { response -> response.body<GroupCreateResponseDto>() }

    suspend fun findMine(): ApiResult<List<GroupResponseDto>> =
        requests.execute(
            ApiRequest(HttpMethod.Get, PATH, RequestKind.READ, monitoringEndpoint = "group_list"),
        ) { response ->
            response.body<List<GroupResponseDto>>()
        }

    suspend fun findById(groupId: String): ApiResult<GroupDetailResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$DETAIL_PATH/$groupId",
                kind = RequestKind.READ,
                monitoringEndpoint = "group_detail",
            ),
        ) { response -> response.body<GroupDetailResponseDto>() }

    suspend fun explore(
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
        const val PATH = "/api/v2/groups"
        const val DETAIL_PATH = "/api/v3/groups"

        // A null cursor must be explicit in the response.
        val responseJson = Json { ignoreUnknownKeys = true }
    }
}
