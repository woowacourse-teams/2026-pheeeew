package com.pheeeew.feature.screens.group.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.feature.screens.group.preview.HomeFixtures

@Preview(name = "그룹 홈 - 빈 상태")
@Composable
private fun GroupHomeEmptyPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.emptyState)
}

@Preview(name = "그룹 홈 - 목록")
@Composable
private fun GroupHomeListPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.listState)
}

@Preview(name = "그룹 홈 - 그룹 하나")
@Composable
private fun GroupHomeSingleGroupPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.singleGroupState)
}

@Preview(name = "그룹 홈 - 조회 실패")
@Composable
private fun GroupHomeFailedPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.failedState)
}

@Preview(name = "그룹 홈 - 새로고침 실패")
@Composable
private fun GroupHomeRefreshFailedPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.refreshFailedState)
}

@Preview(name = "그룹 홈 - 최초 로딩")
@Composable
private fun GroupHomeLoadingPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.loadingState)
}

@Preview(name = "그룹 홈 - 목록 새로고침")
@Composable
private fun GroupHomeRefreshingPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.refreshingState)
}

@Preview(name = "그룹 홈 - 빈 목록 새로고침 실패")
@Composable
private fun GroupHomeEmptyRefreshFailedPreview() {
    GroupHomePreviewFrame(uiState = HomeFixtures.emptyRefreshFailedState)
}

@Composable
private fun GroupHomePreviewFrame(uiState: GroupHomeUiState) {
    GroupHomeScreen(
        uiState = uiState,
        onCreateClick = {},
        onJoinClick = {},
        onGroupClick = {},
        onRetry = {},
        modifier = Modifier.fillMaxSize(),
    )
}
