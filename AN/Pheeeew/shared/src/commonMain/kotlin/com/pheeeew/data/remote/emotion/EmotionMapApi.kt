package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

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
                    groupId?.let { put("groupId", it) }
                }
            }
        return requests.execute(
            ApiRequest(HttpMethod.Get, PATH, RequestKind.READ, queryParameters = query),
        ) { response -> response.body<EmotionMapPageDto>() }
    }

    private companion object {
        const val PATH = "/api/v1/emotions/map"
    }
}
