package com.pheeeew.core.di

import android.content.Context
import android.util.Log
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.data.local.device.AndroidDeviceCredentialStorage
import com.pheeeew.data.remote.device.AndroidPlayIntegrityProofProvider
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.model.device.DeviceSessionDiagnostics

/** Retained across activity recreation; uses only the application context. */
object AndroidApiDependencies {
    private var instance: ApiDependencies? = null
    private var selectedBuild: DeviceSessionBuildConfig? = null

    @Synchronized
    fun get(
        context: Context,
        build: DeviceSessionBuildConfig,
        applicationId: String,
        cloudProjectNumber: Long,
    ): ApiDependencies {
        require(build.isDebug || applicationId == "com.pheeeew") { "Release requires the official package" }
        require(build.attestationMode != "required" || cloudProjectNumber > 0) { "Play Integrity project is required" }
        val partition = ApiConfig(build.baseUrl).deviceStorageEnvironment()
        check(selectedBuild == null || selectedBuild == build)
        return instance ?: ApiDependencies
            .create(
                build,
                AndroidDeviceCredentialStorage(context.applicationContext, partition),
                DevicePlatform.ANDROID,
                createProofProvider = { AndroidPlayIntegrityProofProvider(context, cloudProjectNumber) },
                diagnostics =
                    DeviceSessionDiagnostics { event ->
                        Log.i(
                            "DeviceSession",
                            "env=${build.environment} version=${build.version} mode=${build.attestationMode} $event",
                        )
                    },
            ).also {
                selectedBuild = build
                instance = it
            }
    }
}
