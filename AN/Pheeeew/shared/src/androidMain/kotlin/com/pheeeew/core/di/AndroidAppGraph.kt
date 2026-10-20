package com.pheeeew.core.di

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pheeeew.core.audio.AndroidAudioPlayback
import com.pheeeew.core.audio.AndroidAudioPlayer
import com.pheeeew.core.audio.AndroidVoiceRecorder
import com.pheeeew.core.audio.AudioFactories
import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.MonitoringConfig
import com.pheeeew.core.monitoring.StateDecoder
import com.pheeeew.core.monitoring.createAndroidMonitoring
import com.pheeeew.core.network.AndroidConnectivityObserver
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.permission.AndroidAppSettingsLauncher
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.data.local.device.AndroidDeviceCredentialStorage
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.local.group.AndroidGroupCreateSessionStore
import com.pheeeew.data.local.group.AndroidLastRecordedGroupRepository
import com.pheeeew.data.location.platform.android.AndroidLocationPermissionController
import com.pheeeew.data.location.platform.android.AndroidPlatformLocationProvider
import com.pheeeew.data.remote.attestation.AndroidPlayIntegrityProofProvider
import com.pheeeew.data.remote.attestation.DeviceAttestationPolicy
import com.pheeeew.data.repository.AppVersionRepositoryImpl
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.data.repository.GroupDetailRepositoryImpl
import com.pheeeew.data.repository.GroupListRepositoryImpl
import com.pheeeew.data.repository.GroupStampListRepositoryImpl
import com.pheeeew.data.repository.LocationRepositoryImpl
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.repository.AppVersionRepository
import com.pheeeew.domain.repository.LastRecordedGroupRepository
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.OnboardingStore
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.create.ProcessGroupCreateSessionStore
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlin.random.Random

class AndroidAppConfig(
    val device: DeviceSessionBuildConfig,
    val applicationId: String,
    val cloudProjectNumber: Long,
    val monitoring: MonitoringConfig,
    val version: String,
)

@DependencyGraph(AppScope::class)
internal interface AndroidAppGraph :
    AppGraph,
    ApiBindings {
    val retainedFactory: ActivityRetainedGraph.Factory
    val permissionFactory: AndroidLocationPermissionController.Factory

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides context: Context,
            @Provides config: AndroidAppConfig,
        ): AndroidAppGraph
    }

    @Provides fun build(config: AndroidAppConfig): DeviceSessionBuildConfig {
        require(config.device.isDebug || config.applicationId == "com.pheeeew")
        require(config.device.attestationMode != "required" || config.cloudProjectNumber > 0)
        return config.device
    }

    @Provides @AppVersion
    fun version(config: AndroidAppConfig): String = config.version

    @Provides @PlatformName
    fun platformName(): String = "android"

    @Provides fun platform(): DevicePlatform = DevicePlatform.ANDROID

    @Provides
    @SingleIn(AppScope::class)
    fun monitoringOwner(
        context: Context,
        config: AndroidAppConfig,
    ): AppMonitoring =
        AppMonitoring(
            createAndroidMonitoring(
                context as Application,
                config.monitoring,
                appMonitoringRegistry(),
                StateDecoder(::decodeAppMonitoringState),
            ),
            config.monitoring,
        )

    @Provides fun monitoring(owner: AppMonitoring): Monitoring = owner.runtime

    @Provides
    @SingleIn(AppScope::class)
    fun connectivity(context: Context): ConnectivityObserver = AndroidConnectivityObserver(context)

    @Provides
    @SingleIn(AppScope::class)
    fun storage(
        context: Context,
        build: DeviceSessionBuildConfig,
    ): DeviceCredentialStorage =
        AndroidDeviceCredentialStorage(
            context,
            ApiConfig(build.baseUrl).deviceStorageEnvironment(),
            build.legacyCredentialPolicy,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun attestation(
        context: Context,
        config: AndroidAppConfig,
        build: DeviceSessionBuildConfig,
    ): DeviceAttestationPolicy = build.policy { AndroidPlayIntegrityProofProvider(context, config.cloudProjectNumber) }

    @Provides
    @SingleIn(AppScope::class)
    fun lastGroup(context: Context): LastRecordedGroupRepository = AndroidLastRecordedGroupRepository(context)

    @Provides
    @SingleIn(AppScope::class)
    fun groupDraft(context: Context): GroupCreateSessionStore =
        ProcessGroupCreateSessionStore(AndroidGroupCreateSessionStore(context))

    @Provides
    @SingleIn(AppScope::class)
    fun onboarding(context: Context): OnboardingStore = AndroidOnboardingStore(context)

    @Provides
    @SingleIn(AppScope::class)
    fun settings(context: Context): AppSettingsLauncher = AndroidAppSettingsLauncher(context)

    @Provides fun versions(impl: AppVersionRepositoryImpl): AppVersionRepository = impl

    @Provides
    @SingleIn(AppScope::class)
    fun audio(
        recorder: () -> AndroidVoiceRecorder,
        player: () -> AndroidAudioPlayer,
        playback: () -> AndroidAudioPlayback,
    ): AudioFactories = AudioFactories({ recorder() }, { player() }, { playback() })
}

@GraphExtension(ActivityRetainedScope::class)
internal interface ActivityRetainedGraph :
    UiGraph,
    UiBindings {
    val permissionOwner: AttachedLocationPermission

    @GraphExtension.Factory
    interface Factory {
        fun create(
            @Provides permission: AttachedLocationPermission,
        ): ActivityRetainedGraph
    }

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun services(
        map: EmotionMapRepositoryImpl,
        regions: EmotionRegionMapRepositoryImpl,
        stamps: GroupStampListRepositoryImpl,
        list: GroupListRepositoryImpl,
        detail: GroupDetailRepositoryImpl,
    ): UiServices =
        UiServices(
            map,
            regions,
            stamps,
            list,
            detail,
            GroupOperationKeyAllocator("group-${Random.nextLong().toULong().toString(16)}"),
        )

    @Provides fun permission(owner: AttachedLocationPermission): LocationPermissionController = owner.controller

    @Provides fun provider(impl: AndroidPlatformLocationProvider): PlatformLocationProvider = impl

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun repository(
        permission: LocationPermissionController,
        provider: PlatformLocationProvider,
    ): LocationRepository = LocationRepositoryImpl(permission, provider)

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun location(
        owner: AttachedLocationPermission,
        repository: LocationRepository,
    ): LocationDependencies = LocationDependencies(owner.controller, repository, owner.controller::close)
}

fun createAndroidAppGraph(
    context: Context,
    config: AndroidAppConfig,
): AppGraph = createGraphFactory<AndroidAppGraph.Factory>().create(context.applicationContext, config)

/** The only retained Android owner; it retains no Activity or View. */
class ActivityRetainedGraphOwner private constructor(
    private val app: AndroidAppGraph,
) : ViewModel() {
    private var retained: ActivityRetainedGraph? = null

    fun attach(activity: ComponentActivity): UiGraph {
        val existing = retained
        if (existing != null) {
            existing.permissionOwner.controller.attach(activity)
            return existing
        }
        return app.retainedFactory.create(AttachedLocationPermission(app.permissionFactory.create(activity))).also {
            retained =
                it
        }
    }

    override fun onCleared() {
        retained?.permissionOwner?.controller?.close()
        retained = null
    }

    companion object {
        fun get(
            activity: ComponentActivity,
            app: AppGraph,
        ): ActivityRetainedGraphOwner {
            val factory = viewModelFactory { initializer { ActivityRetainedGraphOwner(app as AndroidAppGraph) } }
            return ViewModelProvider(activity, factory)[ActivityRetainedGraphOwner::class.java]
        }
    }
}

private class AndroidOnboardingStore(
    context: Context,
) : OnboardingStore {
    private val preferences = context.getSharedPreferences("pheeeew_preferences", Context.MODE_PRIVATE)

    override fun isCompleted(): Boolean = preferences.getBoolean("onboarding_completed", false)

    override fun complete() {
        preferences.edit().putBoolean("onboarding_completed", true).apply()
    }
}

internal class AttachedLocationPermission(
    val controller: AndroidLocationPermissionController,
)
