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
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.notification.AndroidNotificationPermissionController
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.permission.AndroidAppSettingsLauncher
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.data.local.device.AndroidDeviceCredentialStorage
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.location.platform.android.AndroidLocationPermissionController
import com.pheeeew.data.location.platform.android.AndroidPlatformLocationProvider
import com.pheeeew.data.remote.attestation.AndroidPlayIntegrityProofProvider
import com.pheeeew.data.remote.attestation.DeviceAttestationPolicy
import com.pheeeew.data.repository.AppVersionRepositoryImpl
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.repository.AppVersionRepository
import com.pheeeew.domain.repository.OnboardingStore
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

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
            @Provides services: AppServiceFactory,
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
    fun connectivity(services: AppServiceFactory): ConnectivityObserver = services.createConnectivityObserver()

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
    val notificationPermissionOwner: AttachedNotificationPermission

    @GraphExtension.Factory
    interface Factory {
        fun create(
            @Provides permission: AttachedLocationPermission,
            @Provides notificationPermission: AttachedNotificationPermission,
        ): ActivityRetainedGraph
    }

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun services(
        map: EmotionMapRepositoryImpl,
        regions: EmotionRegionMapRepositoryImpl,
    ): UiServices =
        UiServices(
            map,
            regions,
        )

    @Provides fun permission(owner: AttachedLocationPermission): LocationPermissionController = owner.controller

    @Provides fun provider(impl: AndroidPlatformLocationProvider): PlatformLocationProvider = impl

    @Provides
    @SingleIn(ActivityRetainedScope::class)
    fun location(
        owner: AttachedLocationPermission,
        permission: LocationPermissionController,
        provider: PlatformLocationProvider,
        services: AppServiceFactory,
    ): LocationDependencies = services.createLocationDependencies(permission, provider, owner.controller::close)
}

fun createAndroidAppGraph(
    context: Context,
    config: AndroidAppConfig,
    services: AppServiceFactory = AndroidAppServiceFactory(context),
): AppGraph = createGraphFactory<AndroidAppGraph.Factory>().create(context.applicationContext, config, services)

/** The only retained Android owner; it retains no Activity or View. */
class ActivityRetainedGraphOwner private constructor(
    private val app: AndroidAppGraph,
) : ViewModel() {
    private var retained: ActivityRetainedGraph? = null
    val notificationPermissionController: NotificationPermissionController
        get() = requireNotNull(retained).notificationPermissionOwner.controller
    var uiSession = AppUiSession()
        private set

    /** Called only after the previous UI composition has been removed. */
    fun clearUiSession() {
        uiSession.close()
        retained?.permissionOwner?.controller?.close()
        retained?.notificationPermissionOwner?.controller?.close()
        retained = null
        uiSession = AppUiSession()
    }

    fun attach(activity: ComponentActivity): UiGraph {
        val existing = retained
        if (existing != null) {
            existing.permissionOwner.controller.attach(activity)
            existing.notificationPermissionOwner.controller.attach(activity)
            return existing
        }
        val graph =
            app.retainedFactory.create(
                AttachedLocationPermission(app.permissionFactory.create(activity)),
                AttachedNotificationPermission(
                    AndroidNotificationPermissionController(
                        activity,
                        app.appSettingsLauncher,
                    ),
                ),
            )
        retained = graph
        return graph
    }

    override fun onCleared() {
        uiSession.close()
        retained?.permissionOwner?.controller?.close()
        retained?.notificationPermissionOwner?.controller?.close()
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
        preferences
            .edit()
            .putBoolean("onboarding_completed", true)
            .putBoolean("usage_guide_completed", false)
            .apply()
    }

    override fun isInitialNotificationPermissionHandled(): Boolean {
        if (!preferences.contains("initial_notification_permission_handled")) {
            // Existing installs must not receive a new automatic prompt after updating.
            preferences.edit().putBoolean("initial_notification_permission_handled", isCompleted()).apply()
        }
        return preferences.getBoolean("initial_notification_permission_handled", false)
    }

    override fun completeInitialNotificationPermission() {
        preferences.edit().putBoolean("initial_notification_permission_handled", true).apply()
    }

    override fun isUsageGuideCompleted(): Boolean = preferences.getBoolean("usage_guide_completed", isCompleted())

    override fun completeUsageGuide() {
        preferences.edit().putBoolean("usage_guide_completed", true).apply()
    }
}

internal class AttachedLocationPermission(
    val controller: AndroidLocationPermissionController,
)

internal class AttachedNotificationPermission(
    val controller: AndroidNotificationPermissionController,
)
