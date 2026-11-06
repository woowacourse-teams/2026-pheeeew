package com.pheeeew.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import kotlin.native.Platform

internal expect val isSimulatorTarget: Boolean

/** A simulator-only, explicit opt-in for transport failure measurements. */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal fun createIosApiEngine(baseUrl: String): HttpClientEngine {
    val info = NSBundle.mainBundle.infoDictionary
    val environment = NSProcessInfo.processInfo.environment
    val enabled =
        Platform.isDebugBinary &&
            info?.get("DEVICE_BUILD_CONFIGURATION") == "Debug" &&
            info["API_BASE_URL"] == "https://api-dev.pheeeew.com" &&
            baseUrl == "https://api-dev.pheeeew.com" &&
            isSimulatorTarget &&
            environment["PHEEEEW_DEBUG_CONNECT_PROXY"] == "1"
    if (!enabled) return Darwin.create()

    // CONNECT carries end-to-end TLS. This does not alter trust evaluation or log traffic.
    return Darwin.create {
        configureSession {
            connectionProxyDictionary =
                mapOf(
                    "HTTPSEnable" to 1,
                    "HTTPSProxy" to "127.0.0.1",
                    "HTTPSPort" to 18771,
                )
        }
    }
}
