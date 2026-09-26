package com.pheeeew.core.network

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
) {
    fun close() = client.close()
}

expect fun createPlatformApiClient(
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider? = null,
): ApiClient

internal fun createApiClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider?,
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
        requests = ApiRequestExecutor(httpClient, config, accessTokenProvider, json),
    )
}
