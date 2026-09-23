package com.pheeeew.legacy.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

actual fun createPlatformHttpClient(config: ApiConfig): HttpClient =
    createHttpClient(
        engine = Android.create(),
        config = config,
    )
