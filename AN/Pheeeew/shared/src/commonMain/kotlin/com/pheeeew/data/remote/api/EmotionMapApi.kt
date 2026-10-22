package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.EmotionMapPageDto
import com.pheeeew.data.remote.mapper.toQueryParameters
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
class EmotionMapApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findPage(
        minLongitude: Double?,
        minLatitude: Double?,
        maxLongitude: Double?,
        maxLatitude: Double?,
        groupId: String?,
        cursor: String?,
        filter: EmotionSearchFilter? = null,
    ): ApiResult<EmotionMapPageDto> {
        val query =
            if (cursor != null) {
                mapOf("cursor" to cursor)
            } else {
                buildMap {
                    minLongitude?.let { put("minLongitude", it.toString()) }
                    minLatitude?.let { put("minLatitude", it.toString()) }
                    maxLongitude?.let { put("maxLongitude", it.toString()) }
                    maxLatitude?.let { put("maxLatitude", it.toString()) }
                    filter?.let { search ->
                        putAll(search.toQueryParameters())
                    } ?: groupId?.let { put("groupId", it) }
                }
            }
        return requests.execute(
            ApiRequest(
                HttpMethod.Get,
                PATH,
                RequestKind.READ,
                monitoringEndpoint = "emotion_map",
                queryParameters = query,
            ),
        ) { response -> response.body<EmotionMapPageDto>() }
    }

    private companion object {
        const val PATH = ApiEndpointPaths.EMOTION_MAP
    }
}
