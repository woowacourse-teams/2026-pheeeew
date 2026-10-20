package com.pheeeew.core.di

import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiAttemptObserver
import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResponseObserver
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.LocationRepositoryImpl
import com.pheeeew.data.repository.PlatformRecordingAudioReader
import com.pheeeew.data.repository.RecordingAudioReader
import com.pheeeew.domain.permission.LocationPermissionController
import io.ktor.client.engine.HttpClientEngine

/** Platform adapters selected by the application composition root. */
abstract class AppServiceFactory {
    abstract fun createApiEngine(): HttpClientEngine

    open fun createApiEngine(config: ApiConfig): HttpClientEngine = createApiEngine()

    fun createApiClient(
        config: ApiConfig,
        accessTokenProvider: AccessTokenProvider? = null,
        observer: ApiResponseObserver? = null,
        attemptObserver: ApiAttemptObserver? = null,
        monitoring: Monitoring = NoOpMonitoring,
    ): ApiClient =
        createApiClient(createApiEngine(config), config, accessTokenProvider, observer, attemptObserver, monitoring)

    abstract fun createConnectivityObserver(): ConnectivityObserver

    open fun createRecordingAudioReader(): RecordingAudioReader = PlatformRecordingAudioReader

    open fun createLocationDependencies(
        permission: LocationPermissionController,
        provider: PlatformLocationProvider,
        close: () -> Unit,
    ): LocationDependencies = LocationDependencies(permission, LocationRepositoryImpl(permission, provider), close)
}
