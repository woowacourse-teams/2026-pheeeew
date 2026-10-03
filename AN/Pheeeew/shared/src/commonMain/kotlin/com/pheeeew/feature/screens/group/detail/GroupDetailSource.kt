package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.model.group.GroupPressBatch
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupId

/** 그룹 ID를 기준으로 상세 화면 스냅샷을 읽습니다. */
fun interface GroupDetailSource {
    suspend fun load(groupId: GroupId): GroupDetailLoadResult
}

sealed interface GroupDetailLoadResult {
    data class Loaded(
        val detail: GroupDetailUiModel,
    ) : GroupDetailLoadResult

    /** 이미 탈퇴한 그룹의 이전 화면에 재진입한 경우도 포함합니다. 통신 오류와 구분합니다. */
    data object MembershipChanged : GroupDetailLoadResult

    /** 그룹이 없거나 삭제됐습니다. MembershipChanged와 다른 문구를 표시합니다. */
    data object NotFound : GroupDetailLoadResult

    data object Unavailable : GroupDetailLoadResult
}

enum class GroupDetailAccessLoss {
    MembershipChanged,
    NotFound,
}

fun interface GroupDetailErrorReporter {
    fun reportUnexpected(exception: Exception)
}

data class GroupDetailRequestPolicy(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    /** 활성 배치를 포함한 수용 후 미확정 탭의 상한입니다. */
    val maxOutstandingPresses: Int = DEFAULT_MAX_OUTSTANDING_PRESSES,
    /** BE `/presses` 계약의 요청 전체 횟수 상한입니다. */
    val maxPressesPerRequest: Int = DEFAULT_MAX_PRESSES_PER_REQUEST,
    /** 첫 입력 뒤 감정 입력을 모으는 전송 창입니다. 입력을 버리는 debounce가 아닙니다. */
    val pressBatchWindowMillis: Long = DEFAULT_PRESS_BATCH_WINDOW_MILLIS,
    val defaultRateLimitDelayMillis: Long = DEFAULT_RATE_LIMIT_DELAY_MILLIS,
) {
    init {
        require(timeoutMillis > 0L) { "timeoutMillis는 양수여야 합니다." }
        require(maxOutstandingPresses > 0) { "maxOutstandingPresses는 양수여야 합니다." }
        require(maxPressesPerRequest in 1..GroupPressBatch.MAX_PRESS_COUNT_PER_REQUEST) {
            "요청당 감정 입력은 1~${GroupPressBatch.MAX_PRESS_COUNT_PER_REQUEST}회여야 합니다."
        }
        require(maxOutstandingPresses >= maxPressesPerRequest) {
            "대기열 상한은 요청당 최대 입력 수 이상이어야 합니다."
        }
        require(pressBatchWindowMillis >= 0L) { "pressBatchWindowMillis는 음수일 수 없습니다." }
        require(defaultRateLimitDelayMillis > 0L) { "defaultRateLimitDelayMillis는 양수여야 합니다." }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val DEFAULT_MAX_OUTSTANDING_PRESSES = 300
        const val DEFAULT_MAX_PRESSES_PER_REQUEST = 100
        const val DEFAULT_PRESS_BATCH_WINDOW_MILLIS = 100L
        const val DEFAULT_RATE_LIMIT_DELAY_MILLIS = 1_000L
    }
}
