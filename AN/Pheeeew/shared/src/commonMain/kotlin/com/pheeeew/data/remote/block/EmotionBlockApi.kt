package com.pheeeew.data.remote.block

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class EmotionBlockRequestDto(
    val emotionId: Long,
)

@Serializable
data class EmotionBlockResponseDto(
    val emotionId: Long,
    val nickname: String,
    val memo: String? = null,
    val createdAt: String,
)

class EmotionBlockApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun create(emotionId: Long): ApiResult<EmotionBlockResponseDto> {
        require(emotionId > 0)
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "/api/v2/blocks/emotions",
                kind = RequestKind.WRITE,
                monitoringEndpoint = "emotion_block",
                body = EmotionBlockRequestDto(emotionId),
            ),
        ) { response -> response.body<EmotionBlockResponseDto>() }
    }
}
