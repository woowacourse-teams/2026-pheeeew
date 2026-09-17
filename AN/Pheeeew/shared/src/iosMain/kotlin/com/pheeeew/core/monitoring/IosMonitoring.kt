@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pheeeew.core.monitoring

import com.posthog.kmp.PostHog
import com.posthog.kmp.PostHogContext
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIDevice

object IosMonitoring {
    val instance: Monitoring by lazy { create() }

    fun foreground() {
        instance.foreground()
    }

    fun background() {
        instance.background()
    }

    private fun create(): Monitoring {
        fun setting(key: String) = NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String ?: ""
        val config =
            MonitoringConfig(
                environment = setting("MONITORING_ENVIRONMENT"),
                platform = "ios",
                appVersion = setting("CFBundleShortVersionString"),
                buildNumber = setting("CFBundleVersion"),
                osVersion = UIDevice.currentDevice.systemVersion,
                enabled = setting("MONITORING_ENABLED") == "true",
                posthogToken = setting("POSTHOG_PROJECT_TOKEN"),
                posthogHost = setting("POSTHOG_HOST"),
                sentryDsn = setting("SENTRY_DSN"),
            )
        val manager = NSFileManager.defaultManager
        val path =
            runCatching {
                val directory =
                    checkNotNull(
                        manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null),
                    ).URLByAppendingPathComponent("Monitoring", true)!!
                check(manager.createDirectoryAtURL(directory, true, null, null))
                check(directory.setResourceValue(true, NSURLIsExcludedFromBackupKey, null))
                checkNotNull(directory.URLByAppendingPathComponent("${config.environment}.json")!!.path)
            }.getOrNull()
        val store =
            object : MonitoringStore {
                override fun read(): String? {
                    val filePath = checkNotNull(path)
                    if (!manager.fileExistsAtPath(filePath)) return null
                    return checkNotNull(NSString.stringWithContentsOfFile(filePath, NSUTF8StringEncoding, null))
                }

                override fun write(value: String) {
                    check(
                        NSString
                            .create(
                                string = value,
                            ).writeToFile(checkNotNull(path), true, NSUTF8StringEncoding, null),
                    )
                }
            }
        val transport = SdkMonitoringTransport()
        val defaults = NSUserDefaults.standardUserDefaults
        val knownNew =
            listOf("onboarding_completed", "first_sigh_guide_completed_v1", "pheeeew_device_id").none {
                defaults.objectForKey(it) !=
                    null
            }
        val monitoring = Monitoring(config, store, transport, newInstallation = knownNew)
        if (config.configured && monitoring.recordingAvailable) {
            runCatching {
                Sentry.initWithPlatformOptions { options ->
                    options.dsn = config.sentryDsn
                    options.environment = config.environment
                    options.releaseName = "pheeeew@${config.appVersion}"
                    options.dist = "ios-${config.buildNumber}"
                    options.sendDefaultPii = false
                    options.attachScreenshot = false
                    options.attachViewHierarchy = false
                    options.enableCaptureFailedRequests = false
                    options.beforeBreadcrumb = { crumb -> if (crumb?.category == "monitoring") crumb else null }
                    options.beforeSend = { event ->
                        event?.apply {
                            request = null
                            message = null
                            extra = null
                            exceptions?.forEach { exception ->
                                (exception as? cocoapods.Sentry.SentryException)?.value =
                                    "[redacted]"
                            }
                        }
                    }
                }
                PostHog.setup(config.posthogConfig(), PostHogContext())
                identifyMonitoring(monitoring.anonymousId, config)
                transport.enabled = true
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            while (true) {
                delay(1000)
                monitoring.tick()
            }
        }
        return monitoring
    }
}
