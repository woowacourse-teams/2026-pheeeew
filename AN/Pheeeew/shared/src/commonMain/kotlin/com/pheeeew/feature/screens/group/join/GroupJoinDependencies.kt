package com.pheeeew.feature.screens.group.join

import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator

/** 그룹 host가 참여 기능에 제공하는 공급자와 작업 식별 정책입니다. */
data class GroupJoinDependencies(
    val lookupGroupAction: LookupGroupAction,
    val joinGroupAction: JoinGroupAction,
    val errorReporter: GroupJoinErrorReporter,
    val operationKeyAllocator: GroupOperationKeyAllocator,
    val requestPolicy: GroupJoinRequestPolicy = GroupJoinRequestPolicy(),
)

/** API 정책이 정해지기 전 조회/참여 작업의 개발용 제한 시간입니다. */
data class GroupJoinRequestPolicy(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(timeoutMillis > 0L) { "timeoutMillis는 양수여야 합니다." }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
