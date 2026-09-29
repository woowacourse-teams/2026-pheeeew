package com.pheeeew.feature.screens.group.preview

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.home.GroupHomeContent
import com.pheeeew.feature.screens.group.home.GroupHomeUiState
import com.pheeeew.feature.screens.group.home.GroupRefreshStatus
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** 그룹 홈 Preview가 사용하는 개발용 고정 데이터입니다. */
internal object HomeFixtures {
    val groups =
        listOf(
            GroupSummaryUiModel(
                id = GroupId("group-hiyu"),
                name = "우테코 8기 히유",
                memberCount = 8,
                weeklyStampCount = 128,
                stamp = appearance("히유", StampShapeId.CIRCLE, 0xFF9DE8D0L),
            ),
            GroupSummaryUiModel(
                id = GroupId("group-after-work"),
                name = "월요일 생존자",
                memberCount = 5,
                weeklyStampCount = 36,
                stamp = appearance("쉼", StampShapeId.TICKET, 0xFFFFC8A8L),
            ),
            GroupSummaryUiModel(
                id = GroupId("group-long-name"),
                name = "퇴근하고 싶다",
                memberCount = 12,
                weeklyStampCount = 82,
                stamp = appearance("기록", StampShapeId.FLOWER, 0xFFF3B9D1L),
            ),
        )

    val emptyState = GroupHomeUiState(content = GroupHomeContent.Empty)
    val emptyRefreshFailedState =
        GroupHomeUiState(content = GroupHomeContent.Empty, refreshStatus = GroupRefreshStatus.Failed)
    val listState = GroupHomeUiState(content = GroupHomeContent.Ready(groups))
    val singleGroupState = GroupHomeUiState(content = GroupHomeContent.Ready(groups.take(1)))
    val failedState = GroupHomeUiState(content = GroupHomeContent.Failed)
    val loadingState = GroupHomeUiState(content = GroupHomeContent.Loading)
    val refreshingState =
        GroupHomeUiState(content = GroupHomeContent.Ready(groups), refreshStatus = GroupRefreshStatus.Refreshing)
    val refreshFailedState =
        GroupHomeUiState(content = GroupHomeContent.Ready(groups), refreshStatus = GroupRefreshStatus.Failed)

    private fun appearance(
        label: String,
        shape: StampShapeId,
        fillArgb: Long,
    ) = StampAppearanceUiModel(
        label = label,
        shape = shape,
        fillArgb = fillArgb,
        textArgb = 0xFF15181BL,
    )
}
