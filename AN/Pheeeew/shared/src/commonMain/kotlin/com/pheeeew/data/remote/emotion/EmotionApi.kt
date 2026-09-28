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
            ApiRequest(HttpMethod.Get, PATH, RequestKind.READ, queryParameters = query),
        ) { it.body<EmotionPageDto>() }

    suspend fun detail(id: Long) =
        requests.execute(
            ApiRequest(HttpMethod.Get, "$PATH/$id", RequestKind.READ),
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
                replayAfterAuthentication = true,
            ),
        ) { Unit }

    private companion object {
        const val PATH = "/api/v1/emotions"
    }
}
