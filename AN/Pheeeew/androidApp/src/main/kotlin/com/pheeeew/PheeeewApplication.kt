package com.pheeeew

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.pheeeew.core.di.AndroidAppConfig
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.di.AppMonitoring
import com.pheeeew.core.di.appCollectionMetadata
import com.pheeeew.core.di.appMonitoringRegistry
import com.pheeeew.core.di.createAndroidAppGraph
import com.pheeeew.core.di.decodeAppMonitoringState
import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.di.monitoringActiveDay
import com.pheeeew.core.monitoring.MonitoringConfig
import com.pheeeew.core.monitoring.StateDecoder
import com.pheeeew.core.monitoring.createAndroidMonitoring

class PheeeewApplication :
    Application(),
    DefaultLifecycleObserver {
    lateinit var appGraph: AppGraph
        private set
    private val owner get() = appGraph.monitoringOwner
    val monitoring get() = owner.runtime

    suspend fun compatibilityMonitoring() = owner.compatibility()

    override fun onCreate() {
        super<Application>.onCreate()
        val config =
            MonitoringConfig(
                environment = BuildConfig.MONITORING_ENVIRONMENT,
                platform = "android",
                appVersion = BuildConfig.VERSION_NAME,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                osVersion = Build.VERSION.RELEASE,
                enabled = BuildConfig.MONITORING_ENABLED,
                posthogToken = BuildConfig.POSTHOG_PROJECT_TOKEN,
                posthogHost = BuildConfig.POSTHOG_HOST,
                sentryDsn = BuildConfig.SENTRY_DSN,
                activeDay = ::monitoringActiveDay,
                collection = appCollectionMetadata(BuildConfig.MONITORING_ENVIRONMENT),
            )
        appGraph =
            createAndroidAppGraph(
                this,
                AndroidAppConfig(
                    DeviceSessionBuildConfig(
                        BuildConfig.DEBUG,
                        BuildConfig.DEVICE_ENVIRONMENT,
                        BuildConfig.API_BASE_URL,
                        BuildConfig.DEVICE_ATTESTATION_MODE,
                        "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
                    ),
                    BuildConfig.APPLICATION_ID,
                    BuildConfig.DEVICE_CLOUD_PROJECT_NUMBER,
                    config,
                    BuildConfig.VERSION_NAME,
                ),
            )
        appGraph.monitoringOwner
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        this.owner.foreground()
    }

    override fun onStop(owner: LifecycleOwner) {
        this.owner.background()
    }
}
