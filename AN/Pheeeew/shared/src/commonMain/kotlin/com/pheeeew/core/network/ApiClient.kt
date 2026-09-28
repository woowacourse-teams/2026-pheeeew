package com.pheeeew.core.network

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * App-scoped HTTP API client shared by feature adapters. The app composition root supplies the
 * platform API URL and optional session provider, owns this instance, and closes it with the
 * app-level dependency graph.
 */
class ApiClient internal constructor(
    private val client: HttpClient,
    val requests: ApiRequestExecutor,
    val monitoring: Monitoring = NoOpMonitoring,
) {
    fun close() = client.close()
}

expect fun createPlatformApiClient(
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider? = null,
    observer: ApiResponseObserver? = null,
    attemptObserver: ApiAttemptObserver? = null,
    monitoring: Monitoring = NoOpMonitoring,
): ApiClient

internal fun createApiClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider?,
    observer: ApiResponseObserver? = null,
    attemptObserver: ApiAttemptObserver? = null,
    monitoring: Monitoring = NoOpMonitoring,
): ApiClient {
    val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    val httpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = config.timeouts.requestMillis
                connectTimeoutMillis = config.timeouts.connectMillis
                socketTimeoutMillis = config.timeouts.socketMillis
            }
        }
    return ApiClient(
        client = httpClient,
        requests = ApiRequestExecutor(httpClient, config, accessTokenProvider, json, observer, attemptObserver),
        monitoring = monitoring,
    )
}
