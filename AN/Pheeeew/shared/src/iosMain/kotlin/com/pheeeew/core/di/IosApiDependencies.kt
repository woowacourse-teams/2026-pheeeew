package com.pheeeew.core.di

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.data.local.device.IosDeviceCredentialStorage
import com.pheeeew.data.remote.device.IosAppAttestProofProvider
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.model.device.DeviceSessionDiagnostics
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/** One session even when SwiftUI recreates its UIViewController. */
object IosApiDependencies {
    @OptIn(ExperimentalNativeApi::class)
    val instance: ApiDependencies by lazy {
        fun setting(key: String): String =
            (NSBundle.mainBundle.infoDictionary?.get(key) as? String)
                ?.takeIf { it.isNotBlank() && !it.contains("$(") }
                ?: error("Missing or unresolved device build setting: $key")
        val configuration = setting("DEVICE_BUILD_CONFIGURATION")
        require(configuration == if (Platform.isDebugBinary) "Debug" else "Release")
        val build =
            DeviceSessionBuildConfig(
                configuration == "Debug",
                setting("DEVICE_ENVIRONMENT"),
                setting("API_BASE_URL"),
                setting("DEVICE_ATTESTATION_MODE"),
                "${setting("CFBundleShortVersionString")}+${setting("CFBundleVersion")}",
            )
        ApiDependencies.create(
            build,
            IosDeviceCredentialStorage(
                ApiConfig(build.baseUrl).deviceStorageEnvironment(),
                build.legacyCredentialPolicy,
            ),
            DevicePlatform.IOS,
            createProofProvider = { IosAppAttestProofProvider() },
            diagnostics =
                DeviceSessionDiagnostics { event ->
                    // All interpolated fields are allowlisted; no object varargs cross the C boundary.
                    NSLog(
                        "DeviceSession env=${build.environment} version=${build.version} mode=${build.attestationMode} $event",
                    )
                },
        )
    }
}
