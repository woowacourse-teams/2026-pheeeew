package com.pheeeew.feature.screens.group.detail

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
    /** 수용 후 서버 결과가 아직 확정되지 않은 입력의 상한입니다. */
    val maxOutstandingPresses: Int = DEFAULT_MAX_OUTSTANDING_PRESSES,
    val defaultRateLimitDelayMillis: Long = DEFAULT_RATE_LIMIT_DELAY_MILLIS,
) {
    init {
        require(timeoutMillis > 0L) { "timeoutMillis는 양수여야 합니다." }
        require(maxOutstandingPresses > 0) { "maxOutstandingPresses는 양수여야 합니다." }
        require(defaultRateLimitDelayMillis > 0L) { "defaultRateLimitDelayMillis는 양수여야 합니다." }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val DEFAULT_MAX_OUTSTANDING_PRESSES = 300
        const val DEFAULT_RATE_LIMIT_DELAY_MILLIS = 1_000L
    }
}
