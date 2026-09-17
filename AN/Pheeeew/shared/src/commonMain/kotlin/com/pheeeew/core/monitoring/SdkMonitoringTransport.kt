package com.pheeeew.core.monitoring

import com.posthog.kmp.CaptureOptions
import com.posthog.kmp.PostHog
import com.posthog.kmp.PostHogBeforeSend
import com.posthog.kmp.PostHogConfig
import com.posthog.kmp.SessionRecordingConfig
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class SdkMonitoringTransport : MonitoringTransport {
    var enabled = false

    override fun track(event: MonitoringEvent): Boolean {
        if (!enabled) return false
        val properties =
            event.properties.mapValues { (_, value) ->
                val primitive = value as JsonPrimitive
                if (primitive.isString) {
                    primitive.content
                } else {
                    primitive.booleanOrNull ?: primitive.longOrNull ?: primitive.doubleOrNull ?: primitive.content
                }
            }
        PostHog.capture(event.name, properties, CaptureOptions(timestamp = event.timestamp))
        runCatching { Sentry.addBreadcrumb(Breadcrumb(category = "monitoring", message = event.name)) }
        return true
    }

    override fun context(snapshot: MonitoringSnapshot?) {
        if (!enabled) return
        Sentry.configureScope { scope ->
            listOf("session_id", "sigh_attempt_id", "save_attempt_id").forEach(scope::removeTag)
            snapshot?.properties()?.forEach { (key, value) -> scope.setTag(key, value) }
        }
    }

    override fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    ) {
        if (!enabled) return
        Sentry.captureException(error) { scope ->
            listOf("session_id", "sigh_attempt_id", "save_attempt_id").forEach(scope::removeTag)
            snapshot?.properties()?.forEach { (key, value) -> scope.setTag(key, value) }
        }
    }
}

internal fun MonitoringConfig.posthogConfig() =
    PostHogConfig(
        apiKey = posthogToken,
        host = posthogHost,
        captureApplicationLifecycleEvents = false,
        captureScreenViews = false,
        captureDeepLinks = false,
        preloadFeatureFlags = false,
        sendFeatureFlagEvent = false,
        sessionRecording = SessionRecordingConfig(enabled = false),
        maxQueueSize = 1000,
        beforeSend =
            listOf(
                PostHogBeforeSend { event ->
                    sanitizePostHogEvent(event)
                },
            ),
    )

internal fun identifyMonitoring(
    id: String,
    config: MonitoringConfig,
) {
    // PostHog beforeSend supplies our distinct ID on every event without an identify alias event.
    Sentry.setUser(User(id = id))
    Sentry.configureScope { it.setTag("app_platform", config.platform) }
}

internal fun sanitizePostHogEvent(event: com.posthog.kmp.PostHogEvent): com.posthog.kmp.PostHogEvent? {
    if (event.event !in MONITORING_EVENTS) return null
    val id = event.properties["anonymous_id"] as? String ?: return null
    if (event.properties["event_id"] !is String) return null
    return event.copy(
        distinctId = id,
        properties = event.properties.filterKeys { it in MONITORING_PROPERTIES } + mapOf("\$geoip_disable" to true),
    )
}

private val MONITORING_EVENTS =
    setOf(
        "app_first_opened",
        "app_visit_started",
        "app_visit_ended",
        "app_active_day",
        "app_backgrounded",
        "sigh_started",
        "sigh_start_failed",
        "sigh_attempt_ended",
        "save_started",
        "save_result",
        "first_sigh_saved",
    )
private val MONITORING_PROPERTIES =
    setOf(
        "anonymous_id",
        "session_id",
        "sigh_attempt_id",
        "save_attempt_id",
        "capture_id",
        "event_id",
        "event_schema_version",
        "measurement_config_version",
        "occurred_at",
        "process_id",
        "event_sequence",
        "screen",
        "state",
        "environment",
        "platform",
        "app_version",
        "build_number",
        "os_version",
        "device_class",
        "install_class",
        "first_opened_at",
        "entry_reason",
        "first_visit",
        "reason",
        "end_time_quality",
        "last_observed_at",
        "activity_date",
        "entry_point",
        "guide_mode",
        "save_index",
        "trigger",
        "min_display_duration_ms",
        "outcome",
        "save_operation_duration_ms",
        "creation_kind",
        "error_code",
        "first_saved_at",
        "cohort_date",
        "first_save_history",
        "last_stage",
        "active_sigh_attempt_id",
    )

object NoOpMonitoringTransport : MonitoringTransport {
    override fun track(event: MonitoringEvent): Boolean = false

    override fun context(snapshot: MonitoringSnapshot?) = Unit

    override fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    ) = Unit
}
