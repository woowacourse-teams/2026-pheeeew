package com.pheeeew.data.remote.sigh.api

import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighPageResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV2PropertiesDto
import com.pheeeew.domain.exception.ApiException
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
                    accessToken?.let { token ->
                        header("Authorization", "Bearer ${token.value}")
                    }
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
                    accessToken?.let { token ->
                        header("Authorization", "Bearer ${token.value}")
                    }
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
                    accessToken?.let { token ->
                        header("Authorization", "Bearer ${token.value}")
                    }
                }
            }
        }
    }

    override suspend fun create(request: SighCreateV2RequestDto): SighFeatureDto<SighV2PropertiesDto> =
        executeAuthenticatedRequest { accessToken ->
            createRequest(request, accessToken)
        }

    private suspend fun <T> executeAuthenticatedRequest(request: suspend (AccessToken?) -> T): T {
        val tokenUsedForRequest = accessTokenForRequest()
        return try {
            request(tokenUsedForRequest)
        } catch (error: ApiException.Unauthorized) {
            if (error.code != "AUTH-001") throw error
            val retryToken =
                refreshMutex.withLock {
                    val latestToken = accessTokenStore?.accessToken
                    if (latestToken != null && latestToken != tokenUsedForRequest) {
                        latestToken
                    } else {
                        refreshAccessToken?.invoke()?.also { refreshedToken ->
                            accessTokenStore?.save(refreshedToken)
                        }
                    }
                } ?: throw error
            request(retryToken)
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

    private fun isRefreshDue(expiresAtEpochSeconds: Long?): Boolean =
        expiresAtEpochSeconds != null &&
            expiresAtEpochSeconds - nowEpochSeconds() <= REFRESH_BEFORE_EXPIRY_SECONDS

    private companion object {
        const val SIGHS_PATH = "/api/v2/sighs"
        const val REFRESH_BEFORE_EXPIRY_SECONDS = 60L
    }
}
