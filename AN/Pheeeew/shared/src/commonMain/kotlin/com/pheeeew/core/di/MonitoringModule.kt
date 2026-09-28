package com.pheeeew.core.di

import com.pheeeew.core.monitoring.ClassificationSource
import com.pheeeew.core.monitoring.CollectionMetadata
import com.pheeeew.core.monitoring.DataSource
import com.pheeeew.core.monitoring.CollectionState
import com.pheeeew.core.monitoring.EventDefinition
import com.pheeeew.core.monitoring.EventEnvelope
import com.pheeeew.core.monitoring.EventRegistry
import com.pheeeew.core.monitoring.LifecycleEvents
import com.pheeeew.core.monitoring.MonitoringConfig
import com.pheeeew.core.monitoring.MonitoringRuntime
import com.pheeeew.core.monitoring.PropertyRule
import com.pheeeew.core.monitoring.RESERVED_PROPERTIES
import com.pheeeew.core.monitoring.ValueType
import com.pheeeew.core.monitoring.monitoringJson
import com.pheeeew.feature.monitoring.compat.MONITORING_PROPERTIES
import com.pheeeew.feature.monitoring.compat.MonitoringEventNames
import com.pheeeew.feature.monitoring.compat.MonitoringTicker
import com.pheeeew.feature.monitoring.network.ApiMonitoringEvents
import com.pheeeew.feature.screens.map.monitoring.RecordSaveEvents
import com.pheeeew.feature.screens.map.monitoring.RecordFunnelEvents
import com.pheeeew.feature.screens.group.monitoring.GroupMonitoringEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import com.pheeeew.feature.monitoring.compat.Monitoring as CompatibilityMonitoring
import com.pheeeew.feature.monitoring.compat.MonitoringConfig as CompatibilityConfig
import com.pheeeew.feature.monitoring.compat.MonitoringState as CompatibilityState

fun appMonitoringRegistry(): EventRegistry {
    val compatibility =
        MonitoringEventNames.definitions.map { old ->
            EventDefinition(
                old.value,
                properties =
                    (MONITORING_PROPERTIES - RESERVED_PROPERTIES).associateWith { key ->
                        PropertyRule(ValueType.PRIMITIVE, required = key in old.requiredProperties)
                    },
                preserveOnOverflow = old.value in setOf("app_first_opened", "first_sigh_saved"),
            )
        }
    return EventRegistry(
        compatibility + LifecycleEvents.definitions + ApiMonitoringEvents.definitions +
            GroupMonitoringEvents.definitions + RecordFunnelEvents.definitions + RecordSaveEvents.definitions,
        setOf(
            "splash",
            "map",
            "settings",
            "legaldocument",
            "onboarding",
            "group_home",
            "group_create",
            "group_detail",
            "ranking",
            "network",
        ),
    )
}

/** Only composition understands the retired state. Core can also run with its default v3 decoder. */
fun decodeAppMonitoringState(raw: String): CollectionState {
    val objectValue = monitoringJson.parseToJsonElement(raw) as JsonObject
    val version = (objectValue["version"] as? JsonPrimitive)?.intOrNull ?: 2
    val loaded =
        when (version) {
            1, 2 -> {
                val old = monitoringJson.decodeFromString<CompatibilityState>(raw)
                CollectionState(
                    anonymousId = old.anonymousId,
                    environment = old.environment,
                    visitId = old.visitId,
                    firstVisitId = old.firstVisitId,
                    firstOpened = old.firstOpened,
                    knownNew = old.knownNew,
                    lastObserved = old.lastObserved,
                    activeDays = old.activeDays,
                    pending = old.pending.map { EventEnvelope(it.name, it.timestamp, it.properties) },
                    extensions =
                        mapOf(
                            "sigh_v2" to
                                monitoringJson.parseToJsonElement(
                                    monitoringJson.encodeToString(old.copy(version = 2, pending = emptyList())),
                                ) as JsonObject,
                        ),
                )
            }

            3 -> {
                monitoringJson.decodeFromString<CollectionState>(raw)
            }

            else -> {
                error("Unsupported monitoring storage version")
            }
        }
    // Recover a legacy producer's unacknowledged events even if its UI is never opened again.
    val extension = loaded.extensions["sigh_v2"] ?: return loaded
    val old = monitoringJson.decodeFromString<CompatibilityState>(extension.toString())
    return loaded.copy(
        pending =
            (
                loaded.pending +
                    old.pending
                        .filterNot {
                            it.name.startsWith(
                                "app_",
                            )
                        }.map { EventEnvelope(it.name, it.timestamp, it.properties) }
            ).distinctBy { it.properties["event_id"] },
        extensions =
            loaded.extensions +
                (
                    "sigh_v2" to
                        monitoringJson.parseToJsonElement(
                            monitoringJson.encodeToString(old.copy(pending = emptyList())),
                        ) as JsonObject
                ),
    )
}

/** App owner. Compatibility is created only when an old screen is opened; it never initializes SDKs. */
class AppMonitoring(
    val runtime: MonitoringRuntime,
    private val config: MonitoringConfig,
) {
    private val lock = Mutex()
    private var compatibility: CompatibilityMonitoring? = null
    private var foreground = false
    private var ticker: MonitoringTicker? = null

    fun foreground() {
        foreground = true
        runtime.foreground()
        compatibility?.foreground()
    }

    fun background() {
        foreground = false
        compatibility?.background()
        runtime.background()
    }

    suspend fun compatibility(): CompatibilityMonitoring =
        withContext(Dispatchers.Main) {
            lock.withLock {
                compatibility ?: createCompatibility().also {
                    compatibility = it
                    if (foreground) it.foreground()
                    ticker =
                        MonitoringTicker(CoroutineScope(SupervisorJob() + Dispatchers.Main), it::tick).also { ticker ->
                            ticker.start()
                        }
                }
            }
        }

    private suspend fun createCompatibility(): CompatibilityMonitoring {
        val initial = runtime.currentState()
        var raw: String? =
            initial?.extensions?.get("sigh_v2")?.toString() ?: initial?.let {
                monitoringJson.encodeToString(
                    CompatibilityState(
                        anonymousId = it.anonymousId,
                        environment = it.environment,
                        firstOpened = it.firstOpened,
                        knownNew = it.knownNew,
                        firstVisitId = it.firstVisitId,
                        activeDays = it.activeDays,
                    ),
                )
            }
        return CompatibilityMonitoring(
            CompatibilityConfig(
                config.environment,
                config.platform,
                config.appVersion,
                config.buildNumber,
                config.osVersion,
                initial != null,
                config.posthogToken,
                config.posthogHost,
                config.sentryDsn,
                config.deviceClass,
            ),
            object : com.pheeeew.feature.monitoring.compat.MonitoringStore {
                override fun read() = raw

                override fun write(value: String) {
                    check(runtime.saveExtension("sigh_v2", monitoringJson.parseToJsonElement(value) as JsonObject))
                    raw = value
                }
            },
            object : com.pheeeew.feature.monitoring.compat.MonitoringTransport {
                override fun track(event: com.pheeeew.feature.monitoring.compat.MonitoringEvent): Boolean {
                    // The new engine owns app visits; retain only old feature analytics here.
                    if (event.name.startsWith("app_")) return true
                    return runtime.replayCompatible(EventEnvelope(event.name, event.timestamp, event.properties))
                }

                override fun context(snapshot: com.pheeeew.feature.monitoring.compat.MonitoringSnapshot?) = Unit

                override fun report(
                    error: Throwable,
                    snapshot: com.pheeeew.feature.monitoring.compat.MonitoringSnapshot?,
                ) {
                    runtime.reportError(error, runtime.context(snapshot?.screen ?: "unknown"))
                }

                override fun flush() {
                    runtime.flush()
                }
            },
        )
    }
}

/** The application's active-day policy; reusable core defaults to UTC. */
fun monitoringActiveDay(timestamp: Long): String =
    kotlin.time.Instant
        .fromEpochMilliseconds(timestamp + 9 * 60 * 60 * 1000)
        .toString()
        .substringBefore('T')

/** Release builds may still be used by QA; production membership stays unknown until classified. */
fun appCollectionMetadata(environment: String) =
    CollectionMetadata(
        productGeneration = "emotion_map",
        dataSource = DataSource.LIVE,
        isTestUser = if (environment == "dev") true else null,
        classificationSource =
            if (environment == "dev") ClassificationSource.BUILD
            else ClassificationSource.UNKNOWN,
    )
