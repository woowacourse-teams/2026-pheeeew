package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.home.GroupHomeScreen
import com.pheeeew.feature.screens.group.home.GroupHomeUiState

@Preview(name = "내 그룹 - 불러오는 중", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeLoadingPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.loadingState)
}

@Preview(name = "내 그룹 - 빈 상태", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeEmptyPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.emptyState)
}

@Preview(name = "내 그룹 - 빈 목록 새로고침 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeEmptyRefreshFailedPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.emptyRefreshFailedState)
}

@Preview(name = "내 그룹 - 첫 조회 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeFailedPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.failedState)
}

@Preview(name = "내 그룹 - 목록", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeListPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.listState)
}

@Preview(name = "내 그룹 - 그룹 하나", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeSingleGroupPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.singleGroupState)
}

@Preview(name = "내 그룹 - 목록 새로고침 중", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeRefreshingPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.refreshingState)
}

@Preview(name = "내 그룹 - 새로고침 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeRefreshFailedPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.refreshFailedState)
}

@Composable
private fun GroupHomePreviewFrame(uiState: GroupHomeUiState) {
    AppTheme {
        GroupHomeScreen(
            uiState = uiState,
            onCreateClick = {},
            onJoinClick = {},
            onGroupClick = {},
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
