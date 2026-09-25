package com.pheeeew.feature.screens.group.home

import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** 그룹 홈에서 화면에 표시할 목록 상태입니다. */
data class GroupHomeUiState(
    val content: GroupHomeContent = GroupHomeContent.Loading,
    val refreshStatus: GroupRefreshStatus = GroupRefreshStatus.Idle,
) {
    val hasSnapshot: Boolean
        get() = content is GroupHomeContent.Empty || content is GroupHomeContent.Ready

    val isRefreshing: Boolean
        get() = refreshStatus == GroupRefreshStatus.Refreshing

    val hasRefreshError: Boolean
        get() = refreshStatus == GroupRefreshStatus.Failed

    init {
        require(hasSnapshot || refreshStatus == GroupRefreshStatus.Idle) {
            "목록 스냅샷이 없을 때는 새로고침 상태를 가질 수 없습니다."
        }
    }
}

/** 기존 목록 스냅샷을 유지하면서 진행하는 갱신의 상태입니다. */
enum class GroupRefreshStatus {
    Idle,
    Refreshing,
    Failed,
}

/** 첫 조회와 목록 스냅샷의 유무를 명시적으로 구분합니다. */
sealed interface GroupHomeContent {
    data object Loading : GroupHomeContent

    data object Empty : GroupHomeContent

    data class Ready(
        val groups: List<GroupSummaryUiModel>,
    ) : GroupHomeContent {
        init {
            require(groups.isNotEmpty()) { "Ready 상태에는 그룹이 한 개 이상 있어야 합니다." }
        }
    }

    data object Failed : GroupHomeContent
}
