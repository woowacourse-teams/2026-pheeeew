package com.pheeeew.core.monitoring

// 환경·앱 버전·SDK 설정
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
) {
    val configured: Boolean get() =
        enabled && environment in setOf("dev", "prod") && posthogToken.isNotBlank() &&
            posthogHost.startsWith("https://") &&
            sentryDsn.startsWith("https://")
}
