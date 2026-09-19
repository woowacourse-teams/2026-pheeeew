package com.pheeeew.core.monitoring

// 이벤트 이름 상수
object MonitoringEventNames {
    val APP_FIRST_OPENED = MonitoringEventDefinition("app_first_opened", setOf("first_opened_at"))
    val APP_VISIT_STARTED = MonitoringEventDefinition("app_visit_started", setOf("entry_reason", "first_visit"))
    val APP_VISIT_ENDED = MonitoringEventDefinition("app_visit_ended", setOf("reason", "end_time_quality"))
    val APP_ACTIVE_DAY = MonitoringEventDefinition("app_active_day", setOf("activity_date"))
    val APP_BACKGROUNDED = MonitoringEventDefinition("app_backgrounded")
    val SIGH_STARTED = MonitoringEventDefinition("sigh_started", setOf("entry_point", "guide_mode"))
    val SIGH_START_FAILED = MonitoringEventDefinition("sigh_start_failed", setOf("reason"))
    val MEMO_SHOWN = MonitoringEventDefinition("memo_shown")
    val MEMO_VALIDATION_FAILED = MonitoringEventDefinition("memo_validation_failed", setOf("reason"))
    val MEMO_COMPLETED = MonitoringEventDefinition("memo_completed", setOf("memo_present", "memo_elapsed_ms"))
    val MEMO_SKIPPED = MonitoringEventDefinition("memo_skipped", setOf("memo_elapsed_ms"))
    val PERMISSION_RESULT = MonitoringEventDefinition("permission_result", setOf("permission", "status"))
    val MIC_START_REQUESTED = MonitoringEventDefinition("mic_start_requested", setOf("capture_index", "trigger"))
    val MIC_READY = MonitoringEventDefinition("mic_ready", setOf("capture_index", "start_to_ready_ms"))
    val MIC_FAILED = MonitoringEventDefinition("mic_failed", setOf("stage", "reason"))
    val MIC_STOPPED = MonitoringEventDefinition("mic_stopped", setOf("reason", "ready_observed"))
    val SOUND_FIRST_DETECTED = MonitoringEventDefinition("sound_first_detected", setOf("strength", "active_threshold"))
    val BASE_SIZE_CHANGED = MonitoringEventDefinition("base_size_changed", setOf("cause", "measurement"))
    val SOUND_GROWTH_STARTED = MonitoringEventDefinition("sound_growth_started", setOf("growth_before", "growth_after"))
    val BREATH_SEGMENT_ENDED = MonitoringEventDefinition("breath_segment_ended", setOf("segment_index", "span_ms", "active_ms", "end_reason"))
    val BREATH_SUMMARY = MonitoringEventDefinition("breath_summary", setOf("segment_count", "final_growth", "detected", "observation_quality", "stop_reason"))
    val SIGH_RELEASE_READY = MonitoringEventDefinition("sigh_release_ready", setOf("growth", "minimum_release_progress"))
    val SIGH_CONTROL_TAPPED = MonitoringEventDefinition("sigh_control_tapped", setOf("tap_index", "phase", "growth", "input_active"))
    val SIGH_SWIPE_ATTEMPTED = MonitoringEventDefinition("sigh_swipe_attempted", setOf("gesture_id", "swipe_index", "outcome", "reason"))
    val SIGH_GESTURE_CANCELLED = MonitoringEventDefinition("sigh_gesture_cancelled", setOf("gesture_id", "reason"))
    val SIGH_RELEASE_SUCCEEDED = MonitoringEventDefinition("sigh_release_succeeded", setOf("gesture_id", "growth_before_release"))
    val SIGH_RELEASE_ANIMATION_FINISHED = MonitoringEventDefinition("sigh_release_animation_finished", setOf("release_to_animation_end_ms"))
    val SIGH_ATTEMPT_ENDED = MonitoringEventDefinition("sigh_attempt_ended", setOf("outcome", "reason", "last_stage", "end_time_quality"))
    val SAVE_STARTED = MonitoringEventDefinition("save_started", setOf("save_index", "trigger", "min_display_duration_ms"))
    val SAVE_RESULT = MonitoringEventDefinition("save_result", setOf("outcome", "creation_kind"))
    val SAVE_UI_RESULT_SHOWN = MonitoringEventDefinition("save_ui_result_shown", setOf("outcome", "save_feedback_elapsed_ms", "min_display_wait_ms", "post_result_to_ui_ms"))
    val API_REQUEST_FINISHED = MonitoringEventDefinition("api_request_finished", setOf("http_attempt_id", "route_template", "method", "http_duration_ms", "outcome"))
    val FIRST_SIGH_SAVED = MonitoringEventDefinition("first_sigh_saved", setOf("first_saved_at", "cohort_date", "first_save_history"))
    val SIGH_SAVED_STAR_VISIBLE = MonitoringEventDefinition("sigh_saved_star_visible", setOf("save_to_star_visible_ms", "foreground_continuous"))
    val MAP_VISIT_STARTED = MonitoringEventDefinition("map_visit_started", setOf("entry_reason"))
    val MAP_STARS_VISIBLE = MonitoringEventDefinition("map_stars_visible", setOf("visible_star_count"))
    val STAR_SELECTED = MonitoringEventDefinition("star_selected", setOf("entry_source"))
    val STAR_DETAIL_SHOWN = MonitoringEventDefinition("star_detail_shown", setOf("entry_source"))
    val STAR_DETAIL_FAILED = MonitoringEventDefinition("star_detail_failed", setOf("entry_source", "reason"))
    val MAP_VISIT_ENDED = MonitoringEventDefinition("map_visit_ended", setOf("reason", "end_time_quality"))

    val definitions: Set<MonitoringEventDefinition> =
        setOf(
            APP_FIRST_OPENED,
            APP_VISIT_STARTED,
            APP_VISIT_ENDED,
            APP_ACTIVE_DAY,
            APP_BACKGROUNDED,
            SIGH_STARTED,
            SIGH_START_FAILED,
            MEMO_SHOWN,
            MEMO_VALIDATION_FAILED,
            MEMO_COMPLETED,
            MEMO_SKIPPED,
            PERMISSION_RESULT,
            MIC_START_REQUESTED,
            MIC_READY,
            MIC_FAILED,
            MIC_STOPPED,
            SOUND_FIRST_DETECTED,
            BASE_SIZE_CHANGED,
            SOUND_GROWTH_STARTED,
            BREATH_SEGMENT_ENDED,
            BREATH_SUMMARY,
            SIGH_RELEASE_READY,
            SIGH_CONTROL_TAPPED,
            SIGH_SWIPE_ATTEMPTED,
            SIGH_GESTURE_CANCELLED,
            SIGH_RELEASE_SUCCEEDED,
            SIGH_RELEASE_ANIMATION_FINISHED,
            SIGH_ATTEMPT_ENDED,
            SAVE_STARTED,
            SAVE_RESULT,
            SAVE_UI_RESULT_SHOWN,
            API_REQUEST_FINISHED,
            FIRST_SIGH_SAVED,
            SIGH_SAVED_STAR_VISIBLE,
            MAP_VISIT_STARTED,
            MAP_STARS_VISIBLE,
            STAR_SELECTED,
            STAR_DETAIL_SHOWN,
            STAR_DETAIL_FAILED,
            MAP_VISIT_ENDED,
        )

    val all: Set<String> = definitions.mapTo(linkedSetOf()) { it.value }
}
