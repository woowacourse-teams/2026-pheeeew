package com.pheeeew.core.network

import io.ktor.client.engine.android.Android

actual fun createPlatformApiClient(
    config: ApiConfig,
    accessTokenProvider: AccessTokenProvider?,
    observer: ApiResponseObserver?,
): ApiClient = createApiClient(Android.create(), config, accessTokenProvider, observer)
