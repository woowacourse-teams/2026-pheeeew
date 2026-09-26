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

    data object Unavailable : GroupDetailLoadResult
}

fun interface GroupDetailErrorReporter {
    fun reportUnexpected(exception: Exception)
}

data class GroupDetailRequestPolicy(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(timeoutMillis > 0L) { "timeoutMillis는 양수여야 합니다." }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
