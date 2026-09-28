package com.pheeeew.core.monitoring

import kotlin.time.Instant

class MonitoringConfig(
    val environment: String,
    val platform: String,
    val appVersion: String,
    val buildNumber: String,
    val osVersion: String,
    val enabled: Boolean,
    val posthogToken: String,
    val posthogHost: String,
    val sentryDsn: String,
    val deviceClass: String = "unknown",
    val applicationName: String = "pheeeew",
    val allowedEnvironments: Set<String> = setOf("dev", "prod"),
    val visitTimeoutMs: Long = 30 * 60 * 1000L,
    val queueCapacity: Int = 1000,
    val activeDay: (Long) -> String = { Instant.fromEpochMilliseconds(it).toString().take(10) },
    val collection: CollectionMetadata = CollectionMetadata(),
) {
    init {
        require(visitTimeoutMs > 0 && queueCapacity > 0)
    }

    val configured: Boolean get() =
        enabled && environment in allowedEnvironments &&
            posthogToken.isNotBlank() && posthogHost.startsWith("https://") && sentryDsn.startsWith("https://")
}

/** Immutable event-time classification. Null participation flags mean unknown, not false. */
data class CollectionMetadata(
    val productGeneration: String = "unknown",
    val dataSource: DataSource = DataSource.UNKNOWN,
    val isTestUser: Boolean? = null,
    val isResearchParticipant: Boolean? = null,
    val classificationSource: ClassificationSource = ClassificationSource.UNKNOWN,
) {
    init {
        require(productGeneration.matches(Regex("[a-z][a-z0-9_]{0,79}")))
    }
}

enum class DataSource(val wireValue: String) {
    LIVE("live"), MOCK("mock"), PREVIEW("preview"), UNKNOWN("unknown"),
}

enum class ClassificationSource(val wireValue: String) {
    BUILD("build"), CONFIGURATION("configuration"), UNKNOWN("unknown"),
}
