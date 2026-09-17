package com.pheeeew.core.monitoring

import android.app.Application
import com.posthog.kmp.PostHog
import com.posthog.kmp.PostHogContext
import io.sentry.android.core.SentryAndroid
import java.io.File

fun createAndroidMonitoring(
    application: Application,
    config: MonitoringConfig,
): Monitoring {
    val file = File(application.noBackupFilesDir, "monitoring-${config.environment}.json")
    val store =
        object : MonitoringStore {
            override fun read(): String? = if (file.exists()) file.readText() else null

            override fun write(value: String) {
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(value)
                check(temp.renameTo(file))
            }
        }
    val transport = SdkMonitoringTransport()
    val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
    val knownNew =
        packageInfo.firstInstallTime == packageInfo.lastUpdateTime &&
            !application.getSharedPreferences("pheeeew_preferences", 0).contains("onboarding_completed")
    val monitoring = Monitoring(config, store, transport, newInstallation = knownNew)
    if (config.configured && monitoring.recordingAvailable) {
        runCatching {
            SentryAndroid.init(application) { options ->
                options.dsn = config.sentryDsn
                options.environment = config.environment
                options.release = "pheeeew@${config.appVersion}"
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
                    event.exceptions?.forEach { it.value = null }
                    event.user =
                        event.user?.let {
                            io.sentry.protocol
                                .User()
                                .apply { id = it.id }
                        }
                    event
                }
            }
            PostHog.setup(config.posthogConfig(), PostHogContext(application))
            identifyMonitoring(monitoring.anonymousId, config)
            transport.enabled = true
        }
    }
    return monitoring
}
