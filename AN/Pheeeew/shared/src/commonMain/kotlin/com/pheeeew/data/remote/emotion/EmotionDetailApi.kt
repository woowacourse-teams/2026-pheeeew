package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json

class EmotionDetailApi(
    private val requests: ApiRequestExecutor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findById(id: Long): ApiResult<EmotionDetailDto> {
        require(id > 0)
        // The server returns application/geo+json, which is decoded explicitly.
        return requests.execute(
            ApiRequest(HttpMethod.Get, "/api/v1/emotions/$id", RequestKind.READ, monitoringEndpoint = "emotion_detail"),
        ) {
            json.decodeFromString<EmotionDetailDto>(it.bodyAsText())
        }
    }
}
