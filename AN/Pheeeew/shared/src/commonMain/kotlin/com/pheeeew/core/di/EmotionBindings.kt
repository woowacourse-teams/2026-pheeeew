package com.pheeeew.core.di

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.data.repository.BlockRepositoryImpl
import com.pheeeew.data.repository.EmotionAudioRepositoryImpl
import com.pheeeew.data.repository.EmotionDetailRepositoryImpl
import com.pheeeew.data.repository.EmotionRegistrationRepositoryImpl
import com.pheeeew.data.repository.RecordingAudioReader
import com.pheeeew.data.repository.ReportRepositoryImpl
import com.pheeeew.domain.repository.BlockRepository
import com.pheeeew.domain.repository.EmotionAudioRepository
import com.pheeeew.domain.repository.EmotionDetailRepository
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.repository.EmotionRegionMapRepository
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.repository.EmotionRepository
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import com.pheeeew.domain.repository.ReportRepository
import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionsUseCase
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.domain.usecase.ReportEmotionUseCase
import com.pheeeew.feature.monitoring.product.MonitoredBlockRepository
import com.pheeeew.feature.monitoring.product.MonitoredEmotionRepository
import com.pheeeew.feature.monitoring.product.MonitoredLocationPermission
import com.pheeeew.feature.screens.map.cache.CacheInvalidator
import dev.zacsweers.metro.Provides
import com.pheeeew.data.repository.EmotionRepositoryImpl as NearbyEmotionRepositoryImpl

internal interface EmotionBindings {
    @Provides fun provideMapRepository(services: UiServices): EmotionMapRepository = services.map

    @Provides fun provideRegionsRepository(services: UiServices): EmotionRegionMapRepository = services.regions

    @Provides fun provideDetailRepository(impl: EmotionDetailRepositoryImpl): EmotionDetailRepository = impl

    @Provides fun provideEmotionRepository(impl: NearbyEmotionRepositoryImpl): EmotionRepository = impl

    @Provides fun provideRegistration(impl: EmotionRegistrationRepositoryImpl): EmotionRegistrationRepository = impl

    @Provides fun provideRecordingAudioReader(services: AppServiceFactory): RecordingAudioReader =
        services.createRecordingAudioReader()

    @Provides fun provideAudio(services: AppServiceFactory): EmotionAudioRepository =
        EmotionAudioRepositoryImpl(services::createApiEngine)

    @Provides fun provideMapDependencies(
        repository: EmotionMapRepository,
        regions: EmotionRegionMapRepository,
        services: UiServices,
    ): EmotionMapDependencies =
        EmotionMapDependencies(
            FindEmotionMapPageUseCase(repository),
            FindEmotionMapSnapshotUseCase(repository),
            FindEmotionRegionsUseCase(regions),
            FindEmotionRegionSnapshotUseCase(regions),
            invalidateSession = {
                services.map.invalidateSession()
                services.regions.invalidateSession()
            },
            invalidateRegions = services.regions::invalidateAll,
        )

    @Provides fun provideBlocks(impl: BlockRepositoryImpl): BlockRepository = impl

    @Provides fun provideReports(impl: ReportRepositoryImpl): ReportRepository = impl

    @Provides fun provideModeration(
        emotions: EmotionRepository,
        blocks: BlockRepository,
        reports: ReportRepository,
        monitoring: Monitoring,
    ): EmotionModerationDependencies =
        EmotionModerationDependencies(
            BlockUserUseCase(MonitoredBlockRepository(blocks, monitoring)),
            DeleteEmotionUseCase(MonitoredEmotionRepository(emotions, monitoring)),
            ReportEmotionUseCase(reports),
        )

    @Provides fun provideRadiusCheck(): IsWithinEmotionRecordRadiusUseCase = IsWithinEmotionRecordRadiusUseCase()

    @Provides fun provideRefreshLocation(
        location: LocationDependencies,
        monitoring: Monitoring,
    ): RefreshLocationUseCase =
        RefreshLocationUseCase(
            MonitoredLocationPermission(location.permissionController, monitoring),
            location.repository,
        )

    @Provides fun provideMapPage(deps: EmotionMapDependencies): FindEmotionMapPageUseCase = deps.findPage

    @Provides fun provideMapSnapshot(deps: EmotionMapDependencies): FindEmotionMapSnapshotUseCase = deps.findSnapshot

    @Provides fun provideRegions(deps: EmotionMapDependencies): FindEmotionRegionsUseCase = deps.findRegions

    @Provides
    fun regionSnapshot(deps: EmotionMapDependencies): FindEmotionRegionSnapshotUseCase = deps.findRegionSnapshot

    // Metro keys include nullability. Optional constructor parameters must not silently use null defaults.

    @Provides fun nullableRegions(deps: EmotionMapDependencies): FindEmotionRegionsUseCase? = deps.findRegions

    @Provides fun nullableRegionSnapshot(deps: EmotionMapDependencies): FindEmotionRegionSnapshotUseCase? =
        deps.findRegionSnapshot

    @Provides fun nullableFilters(repository: MapFilterSettingsRepository): MapFilterSettingsRepository? = repository

    @Provides @InvalidateMapSession
    fun provideSessionInvalidation(deps: EmotionMapDependencies): CacheInvalidator =
        CacheInvalidator(deps.invalidateSession)

    @Provides @InvalidateRegions
    fun provideRegionInvalidation(deps: EmotionMapDependencies): CacheInvalidator =
        CacheInvalidator(deps.invalidateRegions)

    @Provides @MapInitiallyEnabled
    fun provideMapInitiallyEnabled(): Boolean = false
}
