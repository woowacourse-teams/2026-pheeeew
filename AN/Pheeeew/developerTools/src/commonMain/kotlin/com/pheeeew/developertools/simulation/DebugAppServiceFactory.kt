package com.pheeeew.developertools.simulation

import com.pheeeew.core.di.AppServiceFactory
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.data.repository.RecordingAudioReader
import com.pheeeew.domain.permission.LocationPermissionController
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.combine

class DebugAppServiceFactory(
    private val platform: AppServiceFactory,
) : AppServiceFactory() {
    override fun createApiEngine(): HttpClientEngine = DebugNetworkEngine(platform.createApiEngine())

    override fun createApiEngine(config: ApiConfig): HttpClientEngine =
        DebugNetworkEngine(platform.createApiEngine(config), config.baseUrl)

    override fun createConnectivityObserver(): ConnectivityObserver {
        val actual = platform.createConnectivityObserver()
        return object : ConnectivityObserver {
            override val isConnected =
                combine(actual.isConnected, ErrorSimulation.state) { connected, conditions ->
                    connected && !(conditions.developerToolsEnabled && conditions.offline)
                }
        }
    }

    override fun createLocationDependencies(
        permission: LocationPermissionController,
        provider: PlatformLocationProvider,
        close: () -> Unit,
    ): LocationDependencies =
        super.createLocationDependencies(
            SimulatedLocationPermissionController(permission, ErrorSimulation),
            DebugLocationProvider(provider),
            close,
        )

    override fun createRecordingAudioReader(): RecordingAudioReader {
        val actual = platform.createRecordingAudioReader()
        return RecordingAudioReader { path ->
            if (ErrorSimulation.shouldFail(ErrorSimulationScenario.AudioUnavailable)) null else actual.read(path)
        }
    }
}
