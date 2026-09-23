package com.pheeeew.legacy.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(config: ApiConfig): HttpClient =
    createHttpClient(
        engine = Darwin.create(),
        config = config,
    )
