package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.EmotionBlockRequestDto
import com.pheeeew.data.remote.dto.EmotionBlockResponseDto
import com.pheeeew.data.remote.dto.UserBlockResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
class BlockApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun blockEmotion(emotionId: Long): ApiResult<EmotionBlockResponseDto> {
        require(emotionId > 0)
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = ApiEndpointPaths.EMOTION_BLOCK,
                kind = RequestKind.WRITE,
                body = EmotionBlockRequestDto(emotionId),
                monitoringEndpoint = "emotion_block",
                replayAfterAuthentication = true,
            ),
        ) { response -> response.body<EmotionBlockResponseDto>() }
    }

    suspend fun blockUser(emotionId: Long): ApiResult<UserBlockResponseDto> {
        require(emotionId > 0)
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = ApiEndpointPaths.DEVICE_BLOCK,
                kind = RequestKind.WRITE,
                body = EmotionBlockRequestDto(emotionId),
                monitoringEndpoint = "user_block",
                replayAfterAuthentication = true,
            ),
        ) { response -> response.body<UserBlockResponseDto>() }
    }
}
