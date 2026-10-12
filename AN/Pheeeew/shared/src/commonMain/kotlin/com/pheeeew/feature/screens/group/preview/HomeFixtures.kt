package com.pheeeew.feature.screens.group.preview

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.home.GroupHomeContent
import com.pheeeew.feature.screens.group.home.GroupHomeUiState
import com.pheeeew.feature.screens.group.home.GroupRefreshStatus
import com.pheeeew.feature.screens.group.home.explore.GroupExploreContent
import com.pheeeew.feature.screens.group.home.explore.GroupExploreItemUiModel
import com.pheeeew.feature.screens.group.home.explore.GroupExploreUiState
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupJoinType
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
    val exploreState =
        GroupExploreUiState(
            content =
                GroupExploreContent.Ready(
                    items =
                        listOf(
                            exploreGroup(0, "산책 친구", "퇴근 후 가볍게 걷고, 하루를 나눠요.", "산책", 12, GroupJoinType.PUBLIC, false),
                            exploreGroup(1, "주말 산책", "주말 아침 함께 걷는 우리 모임", "주말", 8, GroupJoinType.PRIVATE, false),
                            exploreGroup(2, "동네 산책 모임", "작은 걸음도 함께 기록해요.", "동네", 24, GroupJoinType.PUBLIC, true),
                            exploreGroup(
                                3,
                                "우리 동네 기록단",
                                "비공개로 운영하며 멤버와만 기록을 나눠요.",
                                "기록",
                                6,
                                GroupJoinType.PRIVATE,
                                true,
                            ),
                        ),
                    nextCursor = "demo:4",
                ),
        )
    val exploreSearchState =
        GroupExploreUiState(
            query = "산책",
            content =
                GroupExploreContent.Ready(
                    items =
                        listOf(
                            exploreGroup(0, "산책 친구", "퇴근 후 가볍게 걷고, 하루를 나눠요.", "산책", 12, GroupJoinType.PUBLIC, false),
                            exploreGroup(1, "주말 산책", "주말 아침 함께 걷는 우리 모임", "주말", 8, GroupJoinType.PRIVATE, false),
                        ),
                    nextCursor = null,
                ),
        )
    val exploreLoadingState = GroupExploreUiState(content = GroupExploreContent.Loading)
    val exploreNotLoadedState = GroupExploreUiState(content = GroupExploreContent.NotLoaded)
    val exploreEmptyState = GroupExploreUiState(content = GroupExploreContent.Empty)
    val exploreNoResultsState =
        GroupExploreUiState(query = "찾을 수 없는 그룹", content = GroupExploreContent.Empty)
    val exploreFailedState = GroupExploreUiState(content = GroupExploreContent.Failed)
    val exploreRefreshingState =
        GroupExploreUiState(
            content =
                GroupExploreContent.Ready(
                    items = (exploreState.content as GroupExploreContent.Ready).items,
                    nextCursor = "demo:4",
                    isRefreshing = true,
                ),
        )
    val exploreRefreshFailedState =
        GroupExploreUiState(
            content =
                GroupExploreContent.Ready(
                    items = (exploreState.content as GroupExploreContent.Ready).items,
                    nextCursor = "demo:4",
                    refreshFailed = true,
                ),
        )
    val exploreLoadingMoreState =
        GroupExploreUiState(
            content =
                GroupExploreContent.Ready(
                    items = (exploreState.content as GroupExploreContent.Ready).items,
                    nextCursor = "demo:4",
                    isLoadingMore = true,
                ),
        )
    val exploreLoadMoreFailedState =
        GroupExploreUiState(
            content =
                GroupExploreContent.Ready(
                    items = (exploreState.content as GroupExploreContent.Ready).items,
                    nextCursor = "demo:4",
                    loadMoreFailed = true,
                ),
        )
    val exploreLongContentState =
        GroupExploreUiState(
            content =
                GroupExploreContent.Ready(
                    items =
                        listOf(
                            exploreGroup(
                                4,
                                "아주 길어서 한 줄에서 잘리는 그룹 이름입니다",
                                "그룹 설명이 길어도 카드 높이를 유지하고 한 줄 말줄임으로 표시합니다.",
                                "긴 이름",
                                128,
                                GroupJoinType.PUBLIC,
                                true,
                            ),
                        ),
                    nextCursor = null,
                ),
        )
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

    private fun exploreGroup(
        index: Int,
        name: String,
        description: String,
        stampLabel: String,
        memberCount: Long,
        joinType: GroupJoinType,
        isJoined: Boolean,
    ) = GroupExploreItemUiModel(
        group =
            GroupSummaryUiModel(
                id = GroupId("explore-preview-$index"),
                name = name,
                memberCount = memberCount,
                weeklyStampCount = null,
                stamp = appearance(stampLabel, StampShapeId.ROUNDED_RECTANGLE, 0xFF9DE8D0L),
                description = description,
            ),
        joinType = joinType,
        isJoined = isJoined,
    )
}
