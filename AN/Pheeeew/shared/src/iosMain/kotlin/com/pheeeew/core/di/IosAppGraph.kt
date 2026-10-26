package com.pheeeew.core.di

import com.pheeeew.core.audio.AudioFactories
import com.pheeeew.core.audio.IosAudioPlayback
import com.pheeeew.core.audio.IosAudioPlayer
import com.pheeeew.core.audio.IosVoiceRecorder
import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.core.permission.IosAppSettingsLauncher
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.local.device.IosDeviceCredentialStorage
import com.pheeeew.data.location.platform.ios.IosLocationPermissionController
import com.pheeeew.data.location.platform.ios.IosPlatformLocationProvider
import com.pheeeew.data.remote.attestation.DeviceAttestationPolicy
import com.pheeeew.data.remote.attestation.IosAppAttestProofProvider
import com.pheeeew.data.repository.AppVersionRepositoryImpl
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.repository.AppVersionRepository
import com.pheeeew.domain.repository.OnboardingStore
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import platform.CoreLocation.CLLocationManager
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.random.Random

@DependencyGraph(AppScope::class)
internal interface IosAppGraph :
    AppGraph,
    ApiBindings {
    val controllerFactory: ViewControllerGraph.Factory

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides services: AppServiceFactory,
        ): IosAppGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    @OptIn(ExperimentalNativeApi::class)
    fun build(): DeviceSessionBuildConfig {
        fun setting(key: String): String =
            (NSBundle.mainBundle.infoDictionary?.get(key) as? String)
                ?.takeIf { it.isNotBlank() && !it.contains("$(") } ?: error("Missing device build setting: $key")
        val configuration = setting("DEVICE_BUILD_CONFIGURATION")
        require(configuration == if (Platform.isDebugBinary) "Debug" else "Release")
        return DeviceSessionBuildConfig(
            configuration == "Debug",
            setting("DEVICE_ENVIRONMENT"),
            setting("API_BASE_URL"),
            setting("DEVICE_ATTESTATION_MODE"),
            "${setting("CFBundleShortVersionString")}+${setting("CFBundleVersion")}",
        )
    }

    @Provides @AppVersion
    fun version(): String = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-"

    @Provides @PlatformName
    fun platformName(): String = "ios"

    @Provides fun platform(): DevicePlatform = DevicePlatform.IOS

    @Provides
    @SingleIn(AppScope::class)
    fun monitoringOwner(): AppMonitoring = provideIosMonitoring()

    @Provides fun monitoring(owner: AppMonitoring): Monitoring = owner.runtime

    @Provides
    @SingleIn(AppScope::class)
    fun connectivity(services: AppServiceFactory): ConnectivityObserver = services.createConnectivityObserver()

    @Provides
    @SingleIn(AppScope::class)
    fun storage(build: DeviceSessionBuildConfig): DeviceCredentialStorage =
        IosDeviceCredentialStorage(ApiConfig(build.baseUrl).deviceStorageEnvironment(), build.legacyCredentialPolicy)

    @Provides
    @SingleIn(AppScope::class)
    fun attestation(build: DeviceSessionBuildConfig): DeviceAttestationPolicy =
        build.policy { IosAppAttestProofProvider() }

    @Provides
    @SingleIn(AppScope::class)
    fun onboarding(): OnboardingStore = IosOnboardingStore()

    @Provides
    @SingleIn(AppScope::class)
    fun settings(): AppSettingsLauncher = IosAppSettingsLauncher()

    @Provides fun versions(impl: AppVersionRepositoryImpl): AppVersionRepository = impl

    @Provides
    @SingleIn(AppScope::class)
    fun audio(
        recorder: () -> IosVoiceRecorder,
        player: () -> IosAudioPlayer,
        playback: () -> IosAudioPlayback,
    ): AudioFactories = AudioFactories({ recorder() }, { player() }, { playback() })
}

@GraphExtension(ViewControllerScope::class)
internal interface ViewControllerGraph :
    UiGraph,
    UiBindings {
    @GraphExtension.Factory interface Factory {
        fun create(): ViewControllerGraph
    }

    @Provides
    @SingleIn(ViewControllerScope::class)
    fun services(
        map: EmotionMapRepositoryImpl,
        regions: EmotionRegionMapRepositoryImpl,
    ): UiServices =
        UiServices(
            map,
            regions,
        )

    @Provides
    @SingleIn(ViewControllerScope::class)
    fun locationManager(): CLLocationManager = CLLocationManager()

    @Provides
    @SingleIn(ViewControllerScope::class)
    fun permission(manager: CLLocationManager): IosLocationPermissionController =
        IosLocationPermissionController(manager)

    @Provides
    @SingleIn(ViewControllerScope::class)
    fun provider(manager: CLLocationManager): IosPlatformLocationProvider = IosPlatformLocationProvider(manager)

    @Provides
    @SingleIn(ViewControllerScope::class)
    fun location(
        permission: IosLocationPermissionController,
        provider: IosPlatformLocationProvider,
        services: AppServiceFactory,
    ): LocationDependencies =
        services.createLocationDependencies(permission, provider) {
            permission.close()
            provider.close()
        }
}

internal object IosGraphs {
    private var instance: IosAppGraph? = null

    val app: IosAppGraph get() = initialize(IosAppServiceFactory())

    fun initialize(services: AppServiceFactory): IosAppGraph =
        instance ?: createGraphFactory<IosAppGraph.Factory>().create(services).also { instance = it }
}

/** Select platform adapters before the first use of the process graph. */
fun initializeIosAppGraph(services: AppServiceFactory): AppGraph = IosGraphs.initialize(services)

private class IosOnboardingStore : OnboardingStore {
    override fun isCompleted(): Boolean = NSUserDefaults.standardUserDefaults.boolForKey("onboarding_completed")

    override fun complete() {
        NSUserDefaults.standardUserDefaults.setBool(false, forKey = "usage_guide_completed")
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = "onboarding_completed")
    }

    override fun isUsageGuideCompleted(): Boolean =
        if (NSUserDefaults.standardUserDefaults.objectForKey("usage_guide_completed") == null) {
            isCompleted()
        } else {
            NSUserDefaults.standardUserDefaults.boolForKey("usage_guide_completed")
        }

    override fun completeUsageGuide() {
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = "usage_guide_completed")
    }
}

fun iosDeviceBuildConfig(): DeviceSessionBuildConfig = IosGraphs.app.apiDependencies.buildConfig
