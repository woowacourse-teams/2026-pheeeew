package com.pheeeew.data.remote.block.api

import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.remote.block.dto.DeviceBlockCreateRequestDto
import com.pheeeew.data.remote.block.dto.DeviceBlockResponseDto
import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.device.AccessToken
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class KtorDeviceBlockApi(
    private val client: HttpClient,
    private val accessTokenStore: AccessTokenStore? = null,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
    private val refreshAccessToken: (suspend () -> AccessToken?)? = null,
) : DeviceBlockApi {
    private val refreshMutex = Mutex()

    override suspend fun create(request: DeviceBlockCreateRequestDto): DeviceBlockResponseDto =
        executeAuthenticatedRequest { accessToken ->
            executeRequest {
                client.post(BLOCKS_PATH) {
                    accessToken?.let { header("Authorization", "Bearer ${it.value}") }
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }
        }

    private suspend fun <T> executeAuthenticatedRequest(request: suspend (AccessToken?) -> T): T {
        val tokenUsedForRequest = accessTokenForRequest()
        return try {
            request(tokenUsedForRequest)
        } catch (error: ApiException.Unauthorized) {
            if (error.code != "AUTH-001") throw error
            val retryToken = refreshToken(tokenUsedForRequest) ?: throw error
            request(retryToken)
        }
    }

    private suspend fun accessTokenForRequest(): AccessToken? {
        val store = accessTokenStore ?: return null
        val token = store.accessToken ?: return null
        if (!isRefreshDue(store.accessTokenExpiresAtEpochSeconds)) return token

        return refreshMutex.withLock {
            val latestToken = store.accessToken
            if (!isRefreshDue(store.accessTokenExpiresAtEpochSeconds)) {
                latestToken
            } else {
                refreshAccessToken?.invoke()?.also(store::save) ?: latestToken
            }
        }
    }

    private suspend fun refreshToken(tokenUsedForRequest: AccessToken?): AccessToken? =
        refreshMutex.withLock {
            val latestToken = accessTokenStore?.accessToken
            if (latestToken != null && latestToken != tokenUsedForRequest) {
                latestToken
            } else {
                refreshAccessToken?.invoke()?.also { refreshedToken ->
                    accessTokenStore?.save(refreshedToken)
                }
            }
        }

    private fun isRefreshDue(expiresAtEpochSeconds: Long?): Boolean =
        expiresAtEpochSeconds != null &&
            expiresAtEpochSeconds - nowEpochSeconds() <= REFRESH_BEFORE_EXPIRY_SECONDS

    private companion object {
        const val BLOCKS_PATH = "/api/v2/blocks/devices"
        const val REFRESH_BEFORE_EXPIRY_SECONDS = 60L
    }
}
