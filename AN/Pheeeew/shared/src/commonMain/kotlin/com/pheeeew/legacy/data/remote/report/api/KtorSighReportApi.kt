package com.pheeeew.legacy.data.remote.report.api

import com.pheeeew.legacy.data.local.device.AccessTokenStore
import com.pheeeew.legacy.data.remote.common.executeRequestWithStatus
import com.pheeeew.legacy.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.legacy.data.remote.report.dto.SighReportResponseDto
import com.pheeeew.legacy.data.remote.report.dto.SighReportResultDto
import com.pheeeew.legacy.domain.exception.ApiException
import com.pheeeew.legacy.domain.model.device.AccessToken
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class KtorSighReportApi(
    private val client: HttpClient,
    private val accessTokenStore: AccessTokenStore? = null,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
    private val refreshAccessToken: (suspend () -> AccessToken?)? = null,
) : SighReportApi {
    private val refreshMutex = Mutex()

    override suspend fun create(request: SighReportCreateRequestDto): SighReportResultDto =
        executeAuthenticatedRequest { accessToken ->
            val (status, response) =
                executeRequestWithStatus<SighReportResponseDto> {
                    client.post(REPORTS_PATH) {
                        accessToken?.let { header("Authorization", "Bearer ${it.value}") }
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }
                }
            SighReportResultDto(
                report = response,
                isNew = status == HttpStatusCode.Created,
            )
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
        const val REPORTS_PATH = "/api/v2/reports"
        const val REFRESH_BEFORE_EXPIRY_SECONDS = 60L
    }
}
