package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray

class EmotionRegionMapApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findRegions(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        groupId: String? = null,
    ): ApiResult<JsonArray> =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                "/api/v2/emotions/map/regions",
                RequestKind.READ,
                monitoringEndpoint = "emotion_region_map",
                queryParameters =
                    buildMap {
                        put("minLongitude", bounds.minLongitude.toString())
                        put("minLatitude", bounds.minLatitude.toString())
                        put("maxLongitude", bounds.maxLongitude.toString())
                        put("maxLatitude", bounds.maxLatitude.toString())
                        put("level", level.name)
                        groupId?.let { put("groupId", it) }
                    },
            ),
        ) { response -> response.body<JsonArray>() }
}
