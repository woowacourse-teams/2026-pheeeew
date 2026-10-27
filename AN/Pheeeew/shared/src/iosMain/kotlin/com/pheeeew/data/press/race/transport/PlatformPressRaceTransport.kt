package com.pheeeew.data.press.race.transport

import com.pheeeew.core.network.ApiConfig
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformPressRaceTransport(config: ApiConfig): PressRaceTransport =
    KtorPressRaceTransport(Darwin.create(), config)
