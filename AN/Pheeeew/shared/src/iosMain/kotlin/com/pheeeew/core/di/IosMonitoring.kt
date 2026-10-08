@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pheeeew.core.di

import com.pheeeew.core.monitoring.Monitoring
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

    /** Explicit Debug smoke-test entry point; never creates production activity. */
    fun smokeTestActivity() {
        if (NSBundle.mainBundle.objectForInfoDictionaryKey("MONITORING_ENVIRONMENT") != "dev") return
        val occurredAt =
            kotlin.time.Clock.System
                .now()
                .toEpochMilliseconds()
        com.pheeeew.core.monitoring.ActivityType.entries.forEach {
            instance.recordSuccessfulActivity(it, occurredAt)
        }
        instance.flush()
    }

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
                collection = appCollectionMetadata(setting("MONITORING_ENVIRONMENT")),
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
                        val analyticsReady = runCatching { migrateIosAnalyticsQueue(manager) }.isSuccess
                        if (analyticsReady) PostHog.setup(config.posthogConfig(registry), PostHogContext())
                        identifyMonitoring(anonymousId, config)
                        SdkMonitoringTransport(registry, analyticsReady)
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

/** posthog-ios 3.64.1: Application Support/<bundle>/<token>, plus pre-token legacy storage. */
private fun migrateIosAnalyticsQueue(manager: NSFileManager) {
    val support =
        checkNotNull(manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null))
    val marker = checkNotNull(support.URLByAppendingPathComponent("Monitoring/user-report-v1-native-queue-migrated"))
    if (manager.fileExistsAtPath(checkNotNull(marker.path))) return
    val bundle = checkNotNull(NSBundle.mainBundle.bundleIdentifier)
    val base = checkNotNull(support.URLByAppendingPathComponent(bundle, true))
    val basePath = checkNotNull(base.path)
    val keys =
        listOf(
            "posthog.queueFolder.uuid",
            "posthog.queueFolder",
            "posthog.queue.plist",
            "posthog.replayFolder.uuid",
            "posthog.replayFolder",
            "posthog.replayBufferFolder",
            "posthog.logsFolder",
        )
    if (manager.fileExistsAtPath(basePath)) {
        val children = checkNotNull(manager.contentsOfDirectoryAtPath(basePath, null)).filterIsInstance<String>()
        // Only known queue filenames at the legacy root or direct project-token directories.
        val directories = listOf(basePath) + children.map { "$basePath/$it" }
        for (directory in directories) {
            for (key in keys) {
                val path = "$directory/$key"
                if (manager.fileExistsAtPath(path)) check(manager.removeItemAtPath(path, null))
            }
        }
    }
    check(
        NSString
            .create(
                string = "user_report_v1",
            ).writeToFile(checkNotNull(marker.path), true, NSUTF8StringEncoding, null),
    )
}
