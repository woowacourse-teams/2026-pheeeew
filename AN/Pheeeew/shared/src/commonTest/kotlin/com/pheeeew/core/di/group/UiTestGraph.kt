package com.pheeeew.core.di.group

import com.pheeeew.core.di.ActivityRetainedScope
import com.pheeeew.core.di.AppVersion
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.di.UiBindings
import com.pheeeew.core.di.UiServices
import com.pheeeew.core.di.group.GroupDependencies
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.data.local.mapfilter.InMemoryMapFilterSettingsRepository
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.data.repository.GroupDetailRepositoryImpl
import com.pheeeew.data.repository.GroupListRepositoryImpl
import com.pheeeew.data.repository.GroupStampListRepositoryImpl
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.permission.LocationPermissionStatus
import com.pheeeew.domain.repository.AppVersionRepository
import com.pheeeew.domain.repository.DeviceNicknameRepository
import com.pheeeew.domain.repository.GroupStampListRepository
import com.pheeeew.domain.repository.LastRecordedGroupRepository
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import com.pheeeew.domain.repository.PressRepository
import com.pheeeew.feature.screens.group.create.EmptyGroupCreateSessionStore
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.detail.GroupDetailViewModel
import com.pheeeew.feature.screens.group.members.GroupMembersViewModel
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.map.MapViewModel
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow

/** Exercises the actual production binding definitions against an isolated mock client. */
@DependencyGraph(ActivityRetainedScope::class)
internal interface UiTestGraph : UiBindings {
    val dependencies: GroupDependencies
    val stamps: GroupStampListRepository
    val services: UiServices
    val factory: MetroViewModelFactory
    val mapFactory: MapViewModel.Factory
    val detailFactory: GroupDetailViewModel.Factory
    val membersFactory: GroupMembersViewModel.Factory

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides client: ApiClient,
        ): UiTestGraph
    }

    @Provides fun requests(client: ApiClient): ApiRequestExecutor = client.requests

    @Provides fun monitoring(client: ApiClient): Monitoring = client.monitoring

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun services(
        map: EmotionMapRepositoryImpl,
        regions: EmotionRegionMapRepositoryImpl,
        stamps: GroupStampListRepositoryImpl,
        list: GroupListRepositoryImpl,
        detail: GroupDetailRepositoryImpl,
    ): UiServices = UiServices(map, regions, stamps, list, detail, GroupOperationKeyAllocator("test"))

    @Provides fun drafts(): GroupCreateSessionStore = EmptyGroupCreateSessionStore

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun filters(): MapFilterSettingsRepository = InMemoryMapFilterSettingsRepository()

    @Provides fun lastGroup(): LastRecordedGroupRepository =
        object : LastRecordedGroupRepository {
            override suspend fun readGroupId(): String? = null

            override suspend fun writeGroupId(groupId: String?) = Unit
        }

    @Provides @AppVersion
    fun version(): String = "1.0.0"

    @Provides fun versionRepository(): AppVersionRepository = AppVersionRepository { error("Not used by this test") }

    // These fail if factory construction accidentally instantiates unrelated ViewModels eagerly.
    @Provides fun session(): DeviceSessionManager = error("Unexpected eager session construction")

    @Provides fun nickname(): DeviceNicknameRepository = error("Unexpected eager nickname construction")

    @Provides fun press(): PressRepository = error("Unexpected eager press construction")

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun location(): LocationDependencies =
        LocationDependencies(
            object : LocationPermissionController {
                override suspend fun currentStatus(): LocationPermissionStatus = LocationPermissionStatus.Denied

                override suspend fun requestPermission(): LocationPermissionStatus = LocationPermissionStatus.Denied
            },
            object : LocationRepository {
                override val state = MutableStateFlow<LocationState>(LocationState.Loading)

                override suspend fun refresh() = Unit
            },
        )
}

internal fun createUiTestGraph(client: ApiClient): UiTestGraph =
    createGraphFactory<UiTestGraph.Factory>().create(client)

internal fun createGroupDependencies(client: ApiClient): GroupDependencies = createUiTestGraph(client).dependencies
