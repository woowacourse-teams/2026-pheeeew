package com.pheeeew.feature.screens.group.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.home.GroupHomeScreen
import com.pheeeew.feature.screens.group.home.GroupHomeTab

@Preview(name = "내 그룹 검색 · 부분 일치 결과", widthDp = 360, heightDp = 800)
@Composable
private fun MyGroupSearchResultsPreview() {
    MySearchFrame("우테코", false)
}

@Preview(name = "내 그룹 검색 · 결과 없음", widthDp = 360, heightDp = 800)
@Preview(name = "내 그룹 검색 · 결과 없음·좁은 화면", widthDp = 320, heightDp = 640)
@Composable
private fun MyGroupSearchNoResultsPreview() {
    MySearchFrame("없는 모임", false)
}

@Preview(name = "내 그룹 검색 · 공백 입력 전체 조회", widthDp = 360, heightDp = 800)
@Composable
private fun MyGroupWhitespacePreview() {
    MySearchFrame("   ", true)
}

@Composable
private fun MySearchFrame(
    query: String,
    open: Boolean,
) {
    AppTheme {
        GroupHomeScreen(
            uiState = HomeFixtures.listState,
            onCreateClick = {},
            onJoinClick = {},
            onGroupClick = {},
            onRetry = {},
            selectedTab = GroupHomeTab.MyGroups,
            exploreUiState = HomeFixtures.exploreSearchState,
            initialSearchOpen = open,
            initialMyGroupsQuery = query,
        )
    }
}
