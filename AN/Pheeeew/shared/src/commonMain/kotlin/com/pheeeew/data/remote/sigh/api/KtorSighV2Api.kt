package com.pheeeew.data.remote.sigh.api

import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighPageResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV2PropertiesDto
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.exception.device.DeviceRegistrationException
import com.pheeeew.domain.model.device.AccessToken
import com.pheeeew.domain.model.sigh.SighBounds
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class KtorSighV2Api(
    private val client: HttpClient,
    private val accessTokenStore: AccessTokenStore? = null,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
    private val refreshAccessToken: (suspend () -> AccessToken?)? = null,
) : SighV2Api {
    private val refreshMutex = Mutex()

    override suspend fun getFirstPage(bounds: SighBounds): SighPageResponseDto =
        executeAuthenticatedRequest { accessToken ->
            executeRequest {
                client.get(SIGHS_PATH) {
                    accessToken?.let { header("Authorization", "Bearer ${it.value}") }
                    parameter("minLongitude", bounds.minLongitude)
                    parameter("minLatitude", bounds.minLatitude)
                    parameter("maxLongitude", bounds.maxLongitude)
                    parameter("maxLatitude", bounds.maxLatitude)
                }
            }
        }

    override suspend fun getNextPage(cursor: String): SighPageResponseDto {
        require(cursor.isNotBlank()) { "커서는 비어 있을 수 없습니다." }

        return executeAuthenticatedRequest { accessToken ->
            executeRequest {
                client.get(SIGHS_PATH) {
                    accessToken?.let { header("Authorization", "Bearer ${it.value}") }
                    parameter("cursor", cursor)
                }
            }
        }
    }

    override suspend fun getById(id: Long): SighFeatureDto<SighV2PropertiesDto> {
        require(id > 0) { "한숨 식별자는 양수여야 합니다." }

        return executeAuthenticatedRequest { accessToken ->
            executeRequest {
                client.get("$SIGHS_PATH/$id") {
                    accessToken?.let { header("Authorization", "Bearer ${it.value}") }
                }
            }
        }
    }

    override suspend fun create(request: SighCreateV2RequestDto): SighFeatureDto<SighV2PropertiesDto> =
        run {
            val tokenUsedForRequest = accessTokenForRequest()
            try {
                createRequest(request, tokenUsedForRequest)
            } catch (error: ApiException.Unauthorized) {
                if (error.code != "AUTH-001") throw error
                val retryToken = refreshToken(tokenUsedForRequest) ?: throw error
                createRequest(request, retryToken)
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

    private suspend fun refreshToken(tokenUsedForRequest: AccessToken?): AccessToken? =
        refreshMutex.withLock {
            val latestToken = accessTokenStore?.accessToken
            if (latestToken != null && latestToken != tokenUsedForRequest) {
                latestToken
            } else {
                requestAccessToken()?.also { refreshedToken ->
                    accessTokenStore?.save(refreshedToken)
                }
            }
        }

    private suspend fun createRequest(
        request: SighCreateV2RequestDto,
        accessToken: AccessToken?,
    ): SighFeatureDto<SighV2PropertiesDto> =
        executeRequest {
            client.post(SIGHS_PATH) {
                accessToken?.let { token ->
                    header("Authorization", "Bearer ${token.value}")
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private suspend fun accessTokenForRequest(): AccessToken? {
        val store = accessTokenStore ?: return null
        val token = store.accessToken
        if (token != null && !isRefreshDue(store.accessTokenExpiresAtEpochSeconds)) return token

        return refreshMutex.withLock {
            val latestToken = store.accessToken
            if (latestToken != null && !isRefreshDue(store.accessTokenExpiresAtEpochSeconds)) {
                latestToken
            } else {
                requestAccessToken()?.also(store::save) ?: latestToken
            }
        }
    }

    private suspend fun requestAccessToken(): AccessToken? =
        try {
            refreshAccessToken?.invoke()
        } catch (error: DeviceRegistrationException.Network) {
            throw ApiException.Network(
                code = DEVICE_REGISTRATION_NETWORK_CODE,
                message = error.message.orEmpty(),
            )
        } catch (_: DeviceRegistrationException) {
            throw ApiException.Unknown(
                code = DEVICE_REGISTRATION_FAILURE_CODE,
                message = DEVICE_REGISTRATION_FAILURE_MESSAGE,
            )
        }

    private fun isRefreshDue(expiresAtEpochSeconds: Long?): Boolean =
        expiresAtEpochSeconds != null &&
            expiresAtEpochSeconds - nowEpochSeconds() <= REFRESH_BEFORE_EXPIRY_SECONDS

    private companion object {
        const val SIGHS_PATH = "/api/v2/sighs"
        const val REFRESH_BEFORE_EXPIRY_SECONDS = 60L
        const val DEVICE_REGISTRATION_NETWORK_CODE = "DEVICE_REGISTRATION_NETWORK"
        const val DEVICE_REGISTRATION_FAILURE_CODE = "DEVICE_REGISTRATION_FAILED"
        const val DEVICE_REGISTRATION_FAILURE_MESSAGE =
            "처리 중에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
    }
}
