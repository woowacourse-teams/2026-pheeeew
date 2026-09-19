package com.pheeeew

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.MonitoringConfig
import com.pheeeew.core.monitoring.MonitoringTicker
import com.pheeeew.core.monitoring.createAndroidMonitoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PheeeewApplication :
    Application(),
    DefaultLifecycleObserver {
    lateinit var monitoring: Monitoring
        private set
    private lateinit var monitoringTicker: MonitoringTicker

    override fun onCreate() {
        super<Application>.onCreate()
        monitoring =
            createAndroidMonitoring(
                this,
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
                ),
            )
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        monitoringTicker =
            MonitoringTicker(
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                monitoring::tick,
            ).also { it.start() }
    }

    override fun onStart(owner: LifecycleOwner) {
        monitoring.foreground()
    }

    override fun onStop(owner: LifecycleOwner) {
        monitoring.background()
    }

    override fun onTerminate() {
        monitoringTicker.stop()
        super.onTerminate()
    }
}
