package com.pheeeew.core.network

import com.pheeeew.core.monitoring.Monitoring
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

// ViewModels cancel requests on Main. Avoid HttpURLConnection's blocking stream close there.
internal fun createAndroidApiEngine(): HttpClientEngine = OkHttp.create()

actual fun createPlatformApiClient(
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider?,
    observer: ApiResponseObserver?,
    attemptObserver: ApiAttemptObserver?,
    monitoring: Monitoring,
): ApiClient =
    createApiClient(createAndroidApiEngine(), config, accessTokenProvider, observer, attemptObserver, monitoring)
