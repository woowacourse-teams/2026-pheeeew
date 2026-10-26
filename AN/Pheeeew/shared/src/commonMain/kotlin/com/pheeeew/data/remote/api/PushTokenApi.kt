package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.core.notification.PushTokenRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

class PushTokenApi(
    private val requests: ApiRequestExecutor,
) : PushTokenRepository {
    override suspend fun register(token: String): ApiResult<Unit> =
        requests.executeNoContent(
            ApiRequest(
                method = HttpMethod.Put,
                path = PATH,
                kind = RequestKind.WRITE,
                body = PushTokenRequest(token),
                replayAfterAuthentication = true,
                monitoringEndpoint = "device_push_token",
            ),
        )

    override suspend fun unregister(): ApiResult<Unit> =
        requests.executeNoContent(
            ApiRequest(
                method = HttpMethod.Delete,
                path = PATH,
                kind = RequestKind.WRITE,
                replayAfterAuthentication = true,
                monitoringEndpoint = "device_push_token",
            ),
        )

    @Serializable
    private class PushTokenRequest(
        val token: String,
    ) {
        override fun toString(): String = "PushTokenRequest(<redacted>)"
    }

    private companion object {
        const val PATH = "/api/v3/devices/me/push-token"
    }
}
