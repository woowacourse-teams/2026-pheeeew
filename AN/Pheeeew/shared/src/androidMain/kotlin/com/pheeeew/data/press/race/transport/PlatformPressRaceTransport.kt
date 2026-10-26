package com.pheeeew.data.press.race.transport

import com.pheeeew.core.network.ApiConfig
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createPlatformPressRaceTransport(config: ApiConfig): PressRaceTransport =
    KtorPressRaceTransport(
        OkHttp.create {
            config {
                connectTimeout(config.timeouts.connectMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        },
        config,
    )
