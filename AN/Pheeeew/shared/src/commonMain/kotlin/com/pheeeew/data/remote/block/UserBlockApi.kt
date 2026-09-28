package com.pheeeew.data.remote.block

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
data class UserBlockResponseDto(
    val blockId: Long,
    val emotionId: Long,
    val nickname: String,
    val memo: String? = null,
    val createdAt: String,
)

class UserBlockApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun create(emotionId: Long): ApiResult<UserBlockResponseDto> {
        require(emotionId > 0)
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "/api/v2/blocks/devices",
                kind = RequestKind.WRITE,
                body = EmotionBlockRequestDto(emotionId),
            ),
        ) { response -> response.body<UserBlockResponseDto>() }
    }
}
