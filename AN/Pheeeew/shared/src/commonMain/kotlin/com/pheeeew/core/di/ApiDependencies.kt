package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResponseObserver
import com.pheeeew.core.network.createPlatformApiClient
import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.remote.device.DeviceProofProvider
import com.pheeeew.data.remote.device.KtorDeviceSessionApi
import com.pheeeew.data.repository.DeviceSessionRepositoryImpl
import com.pheeeew.domain.model.device.DeviceDiagnosticOutcome
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.model.device.DeviceSessionDiagnostic
import com.pheeeew.domain.model.device.DeviceSessionDiagnostics
import com.pheeeew.domain.model.device.DeviceSessionStage
import com.pheeeew.domain.model.device.recordSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** App/process owner. Feature dependencies borrow [client] and must not close it. */
class ApiDependencies private constructor(
    private val bootstrap: ApiClient,
    val client: ApiClient,
    val session: DeviceSessionManager,
    private val scope: CoroutineScope,
) {
    fun prepareSession() {
        scope.launch { session.prepare() }
    }

    fun close() {
        scope.cancel()
        client.close()
        bootstrap.close()
    }

    companion object {
        fun create(
            build: DeviceSessionBuildConfig,
            storage: DeviceCredentialStorage,
            platform: DevicePlatform,
            createProofProvider: () -> DeviceProofProvider,
            diagnostics: DeviceSessionDiagnostics = DeviceSessionDiagnostics {},
        ): ApiDependencies {
            val attestationPolicy = build.policy(createProofProvider)
            val config = ApiConfig(build.baseUrl)
            val bootstrap = createPlatformApiClient(config)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val now = { Clock.System.now().toEpochMilliseconds() }
            val session =
                DeviceSessionManager(
                    DeviceSessionRepositoryImpl(
                        KtorDeviceSessionApi(bootstrap.requests, diagnostics),
                        storage,
                        platform,
                        attestationPolicy,
                        now,
                        diagnostics,
                    ),
                    scope,
                    now,
                )
            val observer =
                ApiResponseObserver { status ->
                    diagnostics.recordSafely(
                        DeviceSessionDiagnostic(
                            DeviceSessionStage.AUTHENTICATED_REQUEST,
                            if (status != null &&
                                status in 200..299
                            ) {
                                DeviceDiagnosticOutcome.SUCCEEDED
                            } else {
                                DeviceDiagnosticOutcome.FAILED
                            },
                            statusCode = status,
                        ),
                    )
                }
            return ApiDependencies(bootstrap, createPlatformApiClient(config, session, observer), session, scope)
        }
    }
}

/** Storage partition is selected from the same configuration as the request destination. */
fun ApiConfig.deviceStorageEnvironment(): String =
    when (baseUrl.trimEnd('/')) {
        "https://api-dev.pheeeew.com" -> "dev"
        "https://api.pheeeew.com" -> "prod"
        else -> error("Configure a credential partition for this API environment")
    }
