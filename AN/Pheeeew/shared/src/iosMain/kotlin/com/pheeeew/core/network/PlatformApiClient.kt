package com.pheeeew.core.network

import com.pheeeew.core.monitoring.Monitoring
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformApiClient(
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider?,
    observer: ApiResponseObserver?,
    attemptObserver: ApiAttemptObserver?,
    monitoring: Monitoring,
): ApiClient = createApiClient(Darwin.create(), config, accessTokenProvider, observer, attemptObserver, monitoring)
