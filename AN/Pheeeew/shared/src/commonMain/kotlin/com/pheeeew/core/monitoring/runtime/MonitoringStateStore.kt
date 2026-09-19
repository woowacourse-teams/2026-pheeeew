package com.pheeeew.core.monitoring

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Owns durable monitoring state loading, mutation, and persistence. */
internal class MonitoringStateStore(
    private val store: MonitoringStore,
    private val json: Json,
    config: MonitoringConfig,
    id: () -> String,
    newInstallation: Boolean,
) {
    private val loaded = runCatching { store.read() }
    private val decoded =
        runCatching {
            loaded.getOrNull()?.let { json.decodeFromString<MonitoringState>(it) }
        }
    private val migrated = decoded.mapCatching { it?.let(MonitoringStateMigrator::migrate) }

    var state: MonitoringState =
        migrated.getOrNull()
            ?: MonitoringState(
                anonymousId = id(),
                knownNew = newInstallation && loaded.isSuccess && loaded.getOrNull() == null,
                environment = config.environment,
            )
        private set

    val readable: Boolean
        get() = loaded.isSuccess && migrated.isSuccess

    fun replace(next: MonitoringState) {
        state = next
    }

    fun persist(): Boolean =
        runCatching {
            store.write(json.encodeToString(state))
        }.isSuccess
}
