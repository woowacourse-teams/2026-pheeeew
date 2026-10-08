package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

internal class EmotionRegistrationApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun register(request: EmotionRegistrationRequestDto): ApiResult<EmotionRegistrationResponseDto> =
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
}
