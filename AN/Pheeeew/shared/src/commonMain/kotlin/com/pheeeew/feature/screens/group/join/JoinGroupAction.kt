package com.pheeeew.feature.screens.group.join

import com.pheeeew.feature.screens.group.model.GroupId

/** 조회한 그룹에 참여하는 화면 경계입니다. */
fun interface JoinGroupAction {
    suspend fun join(
        groupId: GroupId,
        normalizedCode: String,
    ): GroupJoinResult
}

sealed interface GroupJoinResult {
    data class Joined(
        val groupId: GroupId,
    ) : GroupJoinResult

    data object Rejected : GroupJoinResult

    /** 참여 요청이 전달됐는지 알 수 없는 결과입니다. 자동 재전송하면 안 됩니다. */
    data object OutcomeUnknown : GroupJoinResult

    data object Unavailable : GroupJoinResult
}

/** 예상하지 못한 공급자 예외를 한 번 기록하기 위한 경계입니다. */
fun interface GroupJoinErrorReporter {
    fun reportUnexpected(error: Exception)
}
