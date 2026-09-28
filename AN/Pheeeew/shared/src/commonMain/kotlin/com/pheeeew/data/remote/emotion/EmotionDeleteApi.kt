package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.http.HttpMethod

class EmotionDeleteApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun delete(emotionId: Long): ApiResult<Unit> {
        require(emotionId > 0)
        return requests.executeNoContent(
            ApiRequest(
                method = HttpMethod.Delete,
                path = "/api/v1/emotions/$emotionId",
                kind = RequestKind.WRITE,
                monitoringEndpoint = "emotion_delete",
                replayAfterAuthentication = false,
            ),
        )
    }
}
