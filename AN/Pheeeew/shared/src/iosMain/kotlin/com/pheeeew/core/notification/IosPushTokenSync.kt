package com.pheeeew.core.notification

import com.pheeeew.core.di.IosGraphs
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.api.PushTokenApi
import com.pheeeew.domain.model.device.DeviceSessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Swift invokes these entry points on the main thread. Work outlives individual Compose screens. */
object IosPushTokenSync {
    private val dependencies get() = IosGraphs.app.apiDependencies
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coordinator by lazy {
        PushTokenSyncCoordinator(
            IosNotificationPermissionController(IosGraphs.app.appSettingsLauncher),
            repository(),
            IosPushTokenStore(dependencies.buildConfig.environment),
            scope,
        ).also { sync ->
            scope.launch {
                dependencies.session.state.collect { session ->
                    sync.sessionChanged((session as? DeviceSessionResult.Ready)?.access?.generation)
                }
            }
            scope.launch {
                dependencies.connectivityObserver.isConnected.collect { connected ->
                    sync.connectivityChanged(connected)
                    if (connected) restoreSession()
                }
            }
            if (dependencies.buildConfig.isDebug) {
                scope.launch {
                    sync.state.collect { state -> println("[PushTokenSync] state=${state.name}") }
                }
            }
        }
    }

    private fun repository(): PushTokenRepository =
        object : PushTokenRepository {
            private val api = PushTokenApi(dependencies.client.requests)

            override suspend fun register(
                token: String,
                expectedSessionGeneration: Long,
            ): ApiResult<Unit> = api.register(token, expectedSessionGeneration).also { report("PUT", it) }

            override suspend fun unregister(expectedSessionGeneration: Long): ApiResult<Unit> =
                api.unregister(expectedSessionGeneration).also { report("DELETE", it) }
        }

    private fun report(
        method: String,
        result: ApiResult<Unit>,
    ) {
        if (!dependencies.buildConfig.isDebug) return
        val outcome =
            when (result) {
                is ApiResult.Success -> {
                    "http=204"
                }

                is ApiResult.Failure -> {
                    val status = (result.reason as? NetworkFailure.HttpStatus)?.statusCode
                    "failure=${result.reason::class.simpleName} http=${status ?: "none"}"
                }
            }
        println("[PushTokenSync] method=$method env=${dependencies.buildConfig.environment} $outcome")
    }

    fun tokenReceived(token: String) = coordinator.tokenReceived(token)

    fun foreground() {
        coordinator.onForeground()
        restoreSession()
    }

    fun background() = coordinator.onBackground()

    fun permissionChanged() = coordinator.permissionChanged()

    private fun restoreSession() {
        scope.launch { dependencies.session.restore() }
    }
}
