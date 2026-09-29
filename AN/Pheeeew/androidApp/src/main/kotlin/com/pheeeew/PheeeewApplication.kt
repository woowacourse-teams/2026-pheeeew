package com.pheeeew

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.pheeeew.core.di.AppMonitoring
import com.pheeeew.core.di.appCollectionMetadata
import com.pheeeew.core.di.appMonitoringRegistry
import com.pheeeew.core.di.decodeAppMonitoringState
import com.pheeeew.core.di.monitoringActiveDay
import com.pheeeew.core.monitoring.MonitoringConfig
import com.pheeeew.core.monitoring.StateDecoder
import com.pheeeew.core.monitoring.createAndroidMonitoring

class PheeeewApplication :
    Application(),
    DefaultLifecycleObserver {
    private lateinit var owner: AppMonitoring
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
        owner =
            AppMonitoring(
                createAndroidMonitoring(
                    this,
                    config,
                    appMonitoringRegistry(),
                    StateDecoder(::decodeAppMonitoringState),
                ),
                config,
            )
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        this.owner.foreground()
    }

    override fun onStop(owner: LifecycleOwner) {
        this.owner.background()
    }
}
