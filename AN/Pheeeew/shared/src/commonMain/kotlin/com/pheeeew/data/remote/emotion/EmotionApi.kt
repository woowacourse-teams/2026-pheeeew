package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

internal class EmotionApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun list(query: Map<String, String>) =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                PATH,
                RequestKind.READ,
                monitoringEndpoint = "emotion_list",
                queryParameters = query,
            ),
        ) { it.body<EmotionPageDto>() }

    suspend fun detail(id: Long) =
        requests.execute(
            ApiRequest(HttpMethod.Get, "$PATH/$id", RequestKind.READ, monitoringEndpoint = "emotion_detail"),
        ) { it.body<EmotionDto>() }

    suspend fun react(
        id: Long,
        type: String,
        selected: Boolean,
    ) = requests.executeNoContent(
        ApiRequest(
            if (selected) HttpMethod.Put else HttpMethod.Delete,
            "$PATH/$id/emojis/$type",
            RequestKind.WRITE,
            monitoringEndpoint = "emotion_reaction",
            replayAfterAuthentication = true,
        ),
    )

    suspend fun block(id: Long) =
        requests.execute(
            ApiRequest(
                HttpMethod.Post,
                "/api/v2/blocks/emotions",
                RequestKind.WRITE,
                body = EmotionBlockRequest(id),
                monitoringEndpoint = "emotion_block",
                replayAfterAuthentication = true,
            ),
        ) { Unit }

    private companion object {
        const val PATH = "/api/v1/emotions"
    }
}
