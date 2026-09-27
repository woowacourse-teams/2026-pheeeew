@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pheeeew.core.di

import com.pheeeew.core.monitoring.MonitoringConfig
import com.pheeeew.core.monitoring.MonitoringRuntime
import com.pheeeew.core.monitoring.MonitoringStore
import com.pheeeew.core.monitoring.SdkMonitoringTransport
import com.pheeeew.core.monitoring.StateDecoder
import com.pheeeew.core.monitoring.identifyMonitoring
import com.pheeeew.core.monitoring.posthogConfig
import com.posthog.kmp.PostHog
import com.posthog.kmp.PostHogContext
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val owner: AppMonitoring by lazy { create() }
    val instance: MonitoringRuntime get() = owner.runtime

    suspend fun compatibility() = owner.compatibility()

    fun foreground() {
        owner.foreground()
    }

    fun background() {
        owner.background()
    }

    private fun create(): AppMonitoring {
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
                activeDay = ::monitoringActiveDay,
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
        val store = IosMonitoringStore(manager, path)
        val registry = appMonitoringRegistry()
        val defaults = NSUserDefaults.standardUserDefaults
        val knownNew =
            listOf("onboarding_completed", "first_sigh_guide_completed_v1", "pheeeew_device_id").none {
                defaults.objectForKey(it) !=
                    null
            }
        val monitoring =
            MonitoringRuntime(
                config,
                registry,
                store,
                StateDecoder(
                    ::decodeAppMonitoringState,
                ),
                newInstallation = knownNew,
                initializeTransport = { anonymousId ->
                    withContext(Dispatchers.Main) {
                        Sentry.initWithPlatformOptions { options ->
                            options.dsn = config.sentryDsn
                            options.environment = config.environment
                            options.releaseName = "${config.applicationName}@${config.appVersion}"
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
                                    user = user?.userId?.let { cocoapods.Sentry.SentryUser(userId = it) }
                                    context =
                                        context?.mapValues { (key, fields) ->
                                            if (key != "device" && key != "app") {
                                                fields
                                            } else {
                                                (fields as? Map<*, *>)
                                                    // Rebuilding native dictionaries changes CFBoolean values to
                                                    // numbers. Omit these optional fields instead of sending invalid data.
                                                    ?.filterKeys { field ->
                                                        val excluded =
                                                            if (key == "device") {
                                                                setOf(
                                                                    "id",
                                                                    "name",
                                                                    "simulator",
                                                                    "charging",
                                                                    "online",
                                                                    "low_memory",
                                                                )
                                                            } else {
                                                                setOf("device_app_hash", "in_foreground", "split_apks")
                                                            }
                                                        field !in excluded
                                                    } ?: fields
                                            }
                                        }
                                    tags = tags?.filterKeys { it != "app.device" }
                                    exceptions?.forEach { exception ->
                                        (exception as? cocoapods.Sentry.SentryException)?.value =
                                            "[redacted]"
                                    }
                                }
                            }
                        }
                        PostHog.setup(config.posthogConfig(registry), PostHogContext())
                        identifyMonitoring(anonymousId, config)
                        SdkMonitoringTransport(registry)
                    }
                },
            )
        return AppMonitoring(monitoring, config)
    }
}

/** Persists monitoring state in Application Support and keeps it excluded from backups. */
private class IosMonitoringStore(
    private val manager: NSFileManager,
    private val path: String?,
) : MonitoringStore {
    override fun read(): String? {
        val filePath = checkNotNull(path)
        if (!manager.fileExistsAtPath(filePath)) return null
        return checkNotNull(NSString.stringWithContentsOfFile(filePath, NSUTF8StringEncoding, null))
    }

    override fun write(value: String) {
        check(
            NSString
                .create(string = value)
                .writeToFile(checkNotNull(path), true, NSUTF8StringEncoding, null),
        )
    }
}
