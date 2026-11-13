package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.mapper.toQueryParameters
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray

@Inject
class EmotionRegionMapApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findRegions(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        filter: EmotionSearchFilter? = null,
    ): ApiResult<JsonArray> =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                ApiEndpointPaths.EMOTION_REGIONS,
                RequestKind.READ,
                monitoringEndpoint = "emotion_region_map",
                queryParameters =
                    buildMap {
                        put("minLongitude", bounds.minLongitude.toString())
                        put("minLatitude", bounds.minLatitude.toString())
                        put("maxLongitude", bounds.maxLongitude.toString())
                        put("maxLatitude", bounds.maxLatitude.toString())
                        put("level", level.name)
                        filter?.let { putAll(it.toQueryParameters()) }
                    },
            ),
        ) { response -> response.body<JsonArray>() }
}
