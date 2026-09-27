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
) {
    init {
        require(visitTimeoutMs > 0 && queueCapacity > 0)
    }

    val configured: Boolean get() =
        enabled && environment in allowedEnvironments &&
            posthogToken.isNotBlank() && posthogHost.startsWith("https://") && sentryDsn.startsWith("https://")
}
