package com.pheeeew.core.monitoring

// 이벤트 이름 상수
object MonitoringEventNames {
    const val APP_FIRST_OPENED = "app_first_opened"
    const val APP_VISIT_STARTED = "app_visit_started"
    const val APP_VISIT_ENDED = "app_visit_ended"
    const val APP_ACTIVE_DAY = "app_active_day"
    const val APP_BACKGROUNDED = "app_backgrounded"
    const val SIGH_STARTED = "sigh_started"
    const val SIGH_START_FAILED = "sigh_start_failed"
    const val MEMO_SHOWN = "memo_shown"
    const val MEMO_VALIDATION_FAILED = "memo_validation_failed"
    const val MEMO_COMPLETED = "memo_completed"
    const val MEMO_SKIPPED = "memo_skipped"
    const val PERMISSION_RESULT = "permission_result"
    const val MIC_START_REQUESTED = "mic_start_requested"
    const val MIC_READY = "mic_ready"
    const val MIC_FAILED = "mic_failed"
    const val MIC_STOPPED = "mic_stopped"
    const val SOUND_FIRST_DETECTED = "sound_first_detected"
    const val BASE_SIZE_CHANGED = "base_size_changed"
    const val SOUND_GROWTH_STARTED = "sound_growth_started"
    const val BREATH_SEGMENT_ENDED = "breath_segment_ended"
    const val BREATH_SUMMARY = "breath_summary"
    const val SIGH_RELEASE_READY = "sigh_release_ready"
    const val SIGH_CONTROL_TAPPED = "sigh_control_tapped"
    const val SIGH_SWIPE_ATTEMPTED = "sigh_swipe_attempted"
    const val SIGH_GESTURE_CANCELLED = "sigh_gesture_cancelled"
    const val SIGH_RELEASE_SUCCEEDED = "sigh_release_succeeded"
    const val SIGH_RELEASE_ANIMATION_FINISHED = "sigh_release_animation_finished"
    const val SIGH_ATTEMPT_ENDED = "sigh_attempt_ended"
    const val SAVE_STARTED = "save_started"
    const val SAVE_RESULT = "save_result"
    const val SAVE_UI_RESULT_SHOWN = "save_ui_result_shown"
    const val API_REQUEST_FINISHED = "api_request_finished"
    const val FIRST_SIGH_SAVED = "first_sigh_saved"
    const val SIGH_SAVED_STAR_VISIBLE = "sigh_saved_star_visible"
    const val MAP_VISIT_STARTED = "map_visit_started"
    const val MAP_STARS_VISIBLE = "map_stars_visible"
    const val STAR_SELECTED = "star_selected"
    const val STAR_DETAIL_SHOWN = "star_detail_shown"
    const val STAR_DETAIL_FAILED = "star_detail_failed"
    const val MAP_VISIT_ENDED = "map_visit_ended"

    val all: Set<String> =
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
}
