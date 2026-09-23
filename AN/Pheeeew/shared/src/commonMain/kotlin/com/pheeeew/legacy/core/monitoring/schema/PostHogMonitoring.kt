package com.pheeeew.legacy.core.monitoring

import com.posthog.kmp.PostHogBeforeSend
import com.posthog.kmp.PostHogConfig
import com.posthog.kmp.SessionRecordingConfig
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.User

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

internal val MONITORING_EVENTS: Set<String> = MonitoringEventNames.all

internal val MONITORING_PROPERTIES =
    setOf(
        "anonymous_id",
        "session_id",
        "sigh_attempt_id",
        "save_attempt_id",
        "capture_id",
        "capture_index",
        "map_visit_id",
        "selection_id",
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
        "memo_present",
        "memo_elapsed_ms",
        "permission_check_id",
        "permission",
        "status",
        "context",
        "start_to_ready_ms",
        "memo_to_ready_ms",
        "strength",
        "active_threshold",
        "tap_to_detection_ms",
        "mic_ready_to_detection_ms",
        "cause",
        "measurement",
        "growth_before",
        "growth_after",
        "ready_to_growth_ms",
        "growth",
        "minimum_release_progress",
        "tap_index",
        "phase",
        "input_active",
        "gesture_id",
        "swipe_index",
        "upward_distance_dp",
        "upward_velocity_dp_s",
        "growth_before_release",
        "release_to_animation_end_ms",
        "stage",
        "ready_observed",
        "segment_index",
        "span_ms",
        "active_ms",
        "gap_before_ms",
        "end_reason",
        "segment_count",
        "final_growth",
        "detected",
        "observation_quality",
        "stop_reason",
        "segment_merge_gap_ms",
        "partial_observation_gap_ms",
        "save_index",
        "trigger",
        "min_display_duration_ms",
        "outcome",
        "save_operation_duration_ms",
        "save_feedback_elapsed_ms",
        "min_display_wait_ms",
        "post_result_to_ui_ms",
        "foreground_continuous",
        "http_attempt_id",
        "route_template",
        "method",
        "http_duration_ms",
        "status_code",
        "correlation_id",
        "creation_kind",
        "error_code",
        "first_saved_at",
        "cohort_date",
        "first_save_history",
        "last_stage",
        "active_sigh_attempt_id",
        "visible_star_count",
        "entry_source",
        "save_to_star_visible_ms",
    )
