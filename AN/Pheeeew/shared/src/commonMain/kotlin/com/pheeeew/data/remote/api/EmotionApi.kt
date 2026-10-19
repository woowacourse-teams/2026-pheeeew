package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.EmotionDetailDto
import com.pheeeew.data.remote.dto.EmotionPageDto
import com.pheeeew.data.remote.dto.EmotionRegistrationRequestDto
import com.pheeeew.data.remote.dto.EmotionRegistrationResponseDto
import com.pheeeew.domain.model.emotion.EmotionReactionType
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json

@Inject
class EmotionApi(
    private val requests: ApiRequestExecutor,
) {
    internal suspend fun list(query: Map<String, String>) =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                PATH,
                RequestKind.READ,
                monitoringEndpoint = "emotion_list",
                queryParameters = query,
            ),
        ) { it.body<EmotionPageDto>() }

    internal suspend fun listWithoutBounds(query: Map<String, String>) =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                V3_PATH,
                RequestKind.READ,
                monitoringEndpoint = "emotion_list_v3",
                queryParameters = query,
            ),
        ) { it.body<EmotionPageDto>() }

    // Decode the application/geo+json detail response once for both domain projections.
    private val detailJson = Json { ignoreUnknownKeys = true }

    suspend fun findById(id: Long): ApiResult<EmotionDetailDto> {
        require(id > 0)
        return requests.execute(
            ApiRequest(HttpMethod.Get, "$PATH/$id", RequestKind.READ, monitoringEndpoint = "emotion_detail"),
        ) { detailJson.decodeFromString<EmotionDetailDto>(it.bodyAsText()) }
    }

    suspend fun react(
        id: Long,
        type: EmotionReactionType,
        selected: Boolean,
    ): ApiResult<Unit> {
        require(id > 0)
        return requests.executeNoContent(
            ApiRequest(
                if (selected) HttpMethod.Put else HttpMethod.Delete,
                "$PATH/$id/emojis/${type.name}",
                RequestKind.WRITE,
                monitoringEndpoint = "emotion_reaction",
                replayAfterAuthentication = true,
            ),
        )
    }

    suspend fun delete(emotionId: Long): ApiResult<Unit> {
        require(emotionId > 0)
        return requests.executeNoContent(
            ApiRequest(
                method = HttpMethod.Delete,
                path = "$PATH/$emotionId",
                kind = RequestKind.WRITE,
                monitoringEndpoint = "emotion_delete",
                replayAfterAuthentication = true,
            ),
        )
    }

    internal suspend fun register(request: EmotionRegistrationRequestDto): ApiResult<EmotionRegistrationResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "/api/v2/emotions",
                kind = RequestKind.WRITE,
                body = request,
                replayAfterAuthentication = true,
                monitoringEndpoint = "emotion_register",
            ),
        ) { it.body<EmotionRegistrationResponseDto>() }

    private companion object {
        const val PATH = "/api/v1/emotions"
        const val V3_PATH = "/api/v3/emotions"
    }
}
