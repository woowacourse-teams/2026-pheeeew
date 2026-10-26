package com.pheeeew.core.di

import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResponseObserver
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.local.mapfilter.InMemoryMapFilterSettingsRepository
import com.pheeeew.data.press.race.PressRaceSession
import com.pheeeew.data.press.race.transport.PressRaceTransport
import com.pheeeew.data.press.race.transport.createPlatformPressRaceTransport
import com.pheeeew.data.remote.api.DeviceNicknameApi
import com.pheeeew.data.remote.api.DeviceSessionApi
import com.pheeeew.data.remote.api.KtorDeviceSessionApi
import com.pheeeew.data.remote.attestation.DeviceAttestationPolicy
import com.pheeeew.data.remote.datasource.PressApiSender
import com.pheeeew.data.remote.datasource.PressApiStatisticsDataSource
import com.pheeeew.data.repository.DeviceNicknameRepositoryImpl
import com.pheeeew.data.repository.DeviceSessionRepositoryImpl
import com.pheeeew.data.repository.PressRepositoryImpl
import com.pheeeew.domain.model.device.DeviceDiagnosticOutcome
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.model.device.DeviceSessionDiagnostic
import com.pheeeew.domain.model.device.DeviceSessionDiagnostics
import com.pheeeew.domain.model.device.DeviceSessionStage
import com.pheeeew.domain.model.device.recordSafely
import com.pheeeew.domain.repository.DeviceNicknameRepository
import com.pheeeew.domain.repository.DeviceSessionRepository
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import com.pheeeew.domain.repository.PressRepository
import com.pheeeew.feature.monitoring.network.ApiMonitoringEvents
import com.pheeeew.feature.monitoring.network.MonitoringApiObserver
import com.pheeeew.feature.monitoring.product.MonitoredDeviceSession
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock

internal interface ApiBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideApiConfig(build: DeviceSessionBuildConfig): ApiConfig = ApiConfig(build.baseUrl)

    @Provides @SessionWork
    @SingleIn(AppScope::class)
    fun provideSessionScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @PressWork
    @SingleIn(AppScope::class)
    fun providePressScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAttemptObserver(monitoring: Monitoring): MonitoringApiObserver = MonitoringApiObserver(monitoring)

    @Provides @Bootstrap
    @SingleIn(AppScope::class)
    fun provideBootstrap(
        config: ApiConfig,
        observer: MonitoringApiObserver,
        monitoring: Monitoring,
        services: AppServiceFactory,
    ): ApiClient = services.createApiClient(config, null, attemptObserver = observer, monitoring = monitoring)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDiagnostics(monitoring: Monitoring): DeviceSessionDiagnostics {
        val diagnostics = DeviceSessionDiagnostics {}
        val diagnosticContext = monitoring.context("network")
        return DeviceSessionDiagnostics { event ->
            diagnostics.recordSafely(event)
            if (event.outcome == DeviceDiagnosticOutcome.FAILED &&
                (event.failureKind != null || event.serverCode != null)
            ) {
                monitoring.track(
                    DefinedEvent(
                        ApiMonitoringEvents.deviceSessionDiagnostic,
                        buildMap {
                            put("stage", EventValue.Text(event.stage.name.lowercase()))
                            put("outcome", EventValue.Text(event.outcome.name.lowercase()))
                            event.failureKind?.let {
                                put("failure_kind", EventValue.Text(it.name.lowercase()))
                            }
                            event.statusCode?.let { put("status_code", EventValue.Integer(it.toLong())) }
                            event.serverCode?.let { put("server_code", EventValue.Text(it)) }
                            event.sdkCode?.let { put("sdk_code", EventValue.Integer(it.toLong())) }
                            event.exceptionType?.let { put("exception_type", EventValue.Text(it)) }
                        },
                    ),
                    diagnosticContext,
                )
            }
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionApi(
        @Bootstrap client: ApiClient,
        diagnostics: DeviceSessionDiagnostics,
    ): DeviceSessionApi = KtorDeviceSessionApi(client.requests, diagnostics)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDeviceRepository(
        api: DeviceSessionApi,
        storage: DeviceCredentialStorage,
        platform: DevicePlatform,
        policy: DeviceAttestationPolicy,
        diagnostics: DeviceSessionDiagnostics,
    ): DeviceSessionRepositoryImpl =
        DeviceSessionRepositoryImpl(
            api,
            storage,
            platform,
            policy,
            { Clock.System.now().toEpochMilliseconds() },
            diagnostics,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun monitoredSession(
        repository: DeviceSessionRepositoryImpl,
        monitoring: Monitoring,
    ): DeviceSessionRepository = MonitoredDeviceSession(repository, monitoring)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSession(
        repository: DeviceSessionRepository,
        @SessionWork scope: CoroutineScope,
    ): DeviceSessionManager =
        DeviceSessionManager(repository, scope, {
            Clock.System.now().toEpochMilliseconds()
        })

    @Provides
    @SingleIn(AppScope::class)
    fun provideClient(
        config: ApiConfig,
        session: DeviceSessionManager,
        diagnostics: DeviceSessionDiagnostics,
        attemptObserver: MonitoringApiObserver,
        monitoring: Monitoring,
        services: AppServiceFactory,
    ): ApiClient =
        services.createApiClient(
            config,
            session,
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
            },
            attemptObserver,
            monitoring,
        )

    @Provides fun provideRequests(client: ApiClient): ApiRequestExecutor = client.requests

    @Provides
    @SingleIn(AppScope::class)
    fun provideNickname(api: DeviceNicknameApi): DeviceNicknameRepositoryImpl = DeviceNicknameRepositoryImpl(api)

    @Provides fun nicknameContract(impl: DeviceNicknameRepositoryImpl): DeviceNicknameRepository = impl

    @Provides
    @SingleIn(AppScope::class)
    fun provideApiDependencies(
        build: DeviceSessionBuildConfig,
        @Bootstrap bootstrap: ApiClient,
        client: ApiClient,
        session: DeviceSessionManager,
        nickname: DeviceNicknameRepositoryImpl,
        connectivity: ConnectivityObserver,
        appSession: AppSession,
        @SessionWork scope: CoroutineScope,
    ): ApiDependencies = ApiDependencies(build, bootstrap, client, session, nickname, connectivity, appSession, scope)

    @Provides
    @SingleIn(AppScope::class)
    fun providePressRepository(
        statistics: PressApiStatisticsDataSource,
        sender: PressApiSender,
        monitoring: Monitoring,
        @PressWork scope: CoroutineScope,
        connectivity: ConnectivityObserver,
    ): PressRepository =
        PressRepositoryImpl(
            statistics,
            sender,
            scope,
            connectivity = connectivity.isConnected,
            monitoring = monitoring,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun providePressRaceTransport(config: ApiConfig): PressRaceTransport = createPlatformPressRaceTransport(config)

    @Provides
    @SingleIn(AppScope::class)
    fun providePressRaceSession(
        transport: PressRaceTransport,
        session: DeviceSessionManager,
        @SessionWork scope: CoroutineScope,
    ): PressRaceSession = PressRaceSession(transport, session, scope)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppSession(
        repository: PressRepository,
        race: PressRaceSession,
        @PressWork scope: CoroutineScope,
    ): AppSession = AppSession(repository, scope, race)

    @Provides
    @SingleIn(AppScope::class)
    fun provideFilters(): MapFilterSettingsRepository = InMemoryMapFilterSettingsRepository()
}
