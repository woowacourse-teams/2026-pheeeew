package com.pheeeew.core.network

import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformApiClient(
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider?,
): ApiClient = createApiClient(Darwin.create(), config, accessTokenProvider)
