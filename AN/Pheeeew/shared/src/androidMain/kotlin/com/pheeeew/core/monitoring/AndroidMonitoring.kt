package com.pheeeew.core.monitoring

import android.app.Application
import com.posthog.kmp.PostHog
import com.posthog.kmp.PostHogContext
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun createAndroidMonitoring(
    application: Application,
    config: MonitoringConfig,
    registry: EventRegistry,
    decoder: StateDecoder,
): MonitoringRuntime {
    val file = File(application.noBackupFilesDir, "monitoring-${config.environment}.json")
    val store = AndroidMonitoringStore(file)
    val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
    val knownNew =
        packageInfo.firstInstallTime == packageInfo.lastUpdateTime &&
            !application.getSharedPreferences("pheeeew_preferences", 0).contains("onboarding_completed")
    return MonitoringRuntime(config, registry, store, decoder, newInstallation = knownNew, initializeTransport = { anonymousId ->
        withContext(Dispatchers.Main) {
            SentryAndroid.init(application) { options ->
                options.dsn = config.sentryDsn
                options.environment = config.environment
                options.release = "${config.applicationName}@${config.appVersion}"
                options.dist = "android-${config.buildNumber}"
                options.isSendDefaultPii = false
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.tracesSampleRate = 0.0
                options.setBeforeBreadcrumb { breadcrumb, _ ->
                    if (breadcrumb.category ==
                        "monitoring"
                    ) {
                        breadcrumb
                    } else {
                        null
                    }
                }
                options.setBeforeSend { event, _ ->
                    event.request = null
                    event.message = null
                    event.serverName = null
                    event.extras = null
                    event.contexts.device?.apply {
                        id = null
                        name = null
                    }
                    event.contexts.app?.deviceAppHash = null
                    event.removeTag("app.device")
                    event.exceptions?.forEach { it.value = "[redacted]" }
                    event.user =
                        event.user?.let {
                            io.sentry.protocol
                                .User()
                                .apply { id = it.id }
                        }
                    event
                }
            }
            PostHog.setup(config.posthogConfig(registry), PostHogContext(application))
            identifyMonitoring(anonymousId, config)
            SdkMonitoringTransport(registry)
        }
    })
}

/** Persists monitoring state in Android's no-backup area, isolated by environment. */
private class AndroidMonitoringStore(
    private val file: File,
) : MonitoringStore {
    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun write(value: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(value)
        check(temp.renameTo(file))
    }
}
