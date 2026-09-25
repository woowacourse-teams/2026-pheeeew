package com.pheeeew.feature.screens.group.home

import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** 홈이 표시할 가입 그룹 목록을 공급하는 Feature 경계입니다. */
interface GroupListSource {
    suspend fun loadGroups(): GroupListResult
}

sealed interface GroupListResult {
    data class Success(
        val groups: List<GroupSummaryUiModel>,
    ) : GroupListResult

    data object Unavailable : GroupListResult
}
