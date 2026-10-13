package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.home.GroupHomeScreen
import com.pheeeew.feature.screens.group.home.GroupHomeTab
import com.pheeeew.feature.screens.group.home.explore.GroupExploreUiState

@Preview(name = "전체 그룹 - 첫 조회 전", widthDp = 360, heightDp = 800)
@Composable
private fun GroupExploreNotLoadedPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreNotLoadedState)
}

@Preview(name = "전체 그룹 - 불러오는 중", widthDp = 360, heightDp = 800)
@Composable
private fun GroupExploreLoadingPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreLoadingState)
}

@Preview(name = "전체 그룹 - 목록", widthDp = 360, heightDp = 920)
@Composable
private fun GroupExploreSearchPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreState)
}

@Preview(name = "전체 그룹 - 검색어 입력 결과", widthDp = 360, heightDp = 800)
@Composable
private fun GroupExploreQueryResultsPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreSearchState)
}

@Preview(name = "전체 그룹 - 그룹 없음", widthDp = 360, heightDp = 800)
@Composable
private fun GroupExploreEmptyPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreEmptyState)
}

@Preview(name = "전체 그룹 - 검색 결과 없음", widthDp = 360, heightDp = 800)
@Composable
private fun GroupExploreNoResultsPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreNoResultsState)
}

@Preview(name = "전체 그룹 - 첫 조회 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupExploreFailurePreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreFailedState)
}

@Preview(name = "전체 그룹 - 새로고침 중", widthDp = 360, heightDp = 920)
@Composable
private fun GroupExploreRefreshingPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreRefreshingState)
}

@Preview(name = "전체 그룹 - 새로고침 실패", widthDp = 360, heightDp = 920)
@Composable
private fun GroupExploreRefreshFailedPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreRefreshFailedState)
}

@Preview(name = "전체 그룹 - 다음 페이지 불러오는 중", widthDp = 360, heightDp = 920)
@Composable
private fun GroupExploreLoadingMorePreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreLoadingMoreState)
}

@Preview(name = "전체 그룹 - 다음 페이지 실패", widthDp = 360, heightDp = 920)
@Composable
private fun GroupExploreLoadMoreFailedPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreLoadMoreFailedState)
}

@Preview(name = "전체 그룹 - 긴 그룹 이름 말줄임", widthDp = 360, heightDp = 800)
@Composable
private fun GroupExploreLongContentPreview() {
    GroupExplorePreviewFrame(uiState = HomeFixtures.exploreLongContentState)
}

@Preview(name = "전체 그룹 - 커서 만료", widthDp = 360, heightDp = 920)
@Composable
private fun GroupExploreExpiredCursorPreview() {
    GroupExplorePreviewFrame(cursorRecoveryState())
}

@Preview(name = "전체 그룹 - 첫 페이지 복구 중", widthDp = 360, heightDp = 920)
@Composable
private fun GroupExploreCursorRecoveryPreview() {
    val state = cursorRecoveryState()
    val ready = state.content as com.pheeeew.feature.screens.group.home.explore.GroupExploreContent.Ready
    GroupExplorePreviewFrame(state.copy(content = ready.copy(isRefreshing = true, loadMoreFailed = false)))
}

@Preview(name = "전체 그룹 - 첫 페이지 복구 실패", widthDp = 320, heightDp = 920)
@Composable
private fun GroupExploreCursorRecoveryFailedPreview() {
    val state = cursorRecoveryState()
    val ready = state.content as com.pheeeew.feature.screens.group.home.explore.GroupExploreContent.Ready
    GroupExplorePreviewFrame(state.copy(content = ready.copy(refreshFailed = true, loadMoreFailed = false)))
}

private fun cursorRecoveryState(): GroupExploreUiState {
    val state = HomeFixtures.exploreState
    val ready = state.content as com.pheeeew.feature.screens.group.home.explore.GroupExploreContent.Ready
    return state.copy(content = ready.copy(loadMoreFailed = true, requiresFirstPageReload = true))
}

@Composable
private fun GroupExplorePreviewFrame(uiState: GroupExploreUiState) {
    AppTheme {
        GroupHomeScreen(
            uiState = HomeFixtures.listState,
            onCreateClick = {},
            onJoinClick = {},
            onGroupClick = {},
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
            selectedTab = GroupHomeTab.Explore,
            exploreUiState = uiState,
        )
    }
}
