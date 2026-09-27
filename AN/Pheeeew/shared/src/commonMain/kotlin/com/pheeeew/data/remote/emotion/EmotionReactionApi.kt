package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.domain.model.emotion.EmotionReactionType
import io.ktor.http.HttpMethod

class EmotionReactionApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun setSelected(
        emotionId: Long,
        type: EmotionReactionType,
        selected: Boolean,
    ): ApiResult<Unit> {
        require(emotionId > 0)
        return requests.executeNoContent(
            ApiRequest(
                method = if (selected) HttpMethod.Put else HttpMethod.Delete,
                path = "/api/v1/emotions/$emotionId/emojis/${type.name}",
                kind = RequestKind.WRITE,
                replayAfterAuthentication = true,
            ),
        )
    }
}
