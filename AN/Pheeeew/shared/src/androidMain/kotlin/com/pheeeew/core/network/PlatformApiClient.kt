package com.pheeeew.core.network

import com.pheeeew.core.monitoring.Monitoring
import io.ktor.client.engine.android.Android

actual fun createPlatformApiClient(
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider?,
    observer: ApiResponseObserver?,
    attemptObserver: ApiAttemptObserver?,
    monitoring: Monitoring,
): ApiClient = createApiClient(Android.create(), config, accessTokenProvider, observer, attemptObserver, monitoring)
