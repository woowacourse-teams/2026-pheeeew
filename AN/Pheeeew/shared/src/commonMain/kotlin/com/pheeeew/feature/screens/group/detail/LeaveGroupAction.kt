package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.model.GroupId

fun interface LeaveGroupAction {
    suspend fun leave(groupId: GroupId): LeaveGroupResult
}

sealed interface LeaveGroupResult {
    data object Left : LeaveGroupResult

    /** 서버 반영 여부를 판단할 수 없는 결과입니다. 나가기 요청을 자동 재전송하지 않습니다. */
    data object OutcomeUnknown : LeaveGroupResult

    /** 요청이 반영되지 않은 것이 확인된 경우에만 반환합니다. */
    data object Unavailable : LeaveGroupResult
}
