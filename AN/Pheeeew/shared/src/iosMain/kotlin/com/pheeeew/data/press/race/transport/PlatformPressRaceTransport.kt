package com.pheeeew.data.press.race.transport

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createIosApiEngine

internal actual fun createPlatformPressRaceTransport(config: ApiConfig): PressRaceTransport =
    KtorPressRaceTransport(createIosApiEngine(config.baseUrl), config)
