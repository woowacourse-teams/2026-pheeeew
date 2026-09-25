package com.pheeeew.feature.screens.group.join

import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** 초대 코드로 참여 가능한 그룹을 조회하는 화면 경계입니다. */
fun interface LookupGroupAction {
    suspend fun find(normalizedCode: String): GroupLookupResult
}

sealed interface GroupLookupResult {
    data class Found(
        val group: GroupSummaryUiModel,
    ) : GroupLookupResult

    data object NotFound : GroupLookupResult

    data object Unavailable : GroupLookupResult
}
