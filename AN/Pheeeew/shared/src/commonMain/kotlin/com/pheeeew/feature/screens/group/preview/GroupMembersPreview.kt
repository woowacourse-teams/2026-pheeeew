package com.pheeeew.feature.screens.group.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.members.GroupMemberUiModel
import com.pheeeew.feature.screens.group.members.GroupMembersContent
import com.pheeeew.feature.screens.group.members.GroupMembersScreen
import com.pheeeew.feature.screens.group.members.GroupMembersUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview(name = "멤버 · 첫 로딩", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersLoadingPreview() {
    GroupMembersPreviewFrame(GroupMembersContent.Loading)
}

@Preview(name = "멤버 · 가입순 목록", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersListPreview() {
    GroupMembersPreviewFrame(
        GroupMembersContent.Ready(
            items = previewMembers(),
            nextCursor = "demo:members:8",
            generation = 1,
        ),
    )
}

@Preview(name = "멤버 · 마지막 페이지", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersLastPagePreview() {
    GroupMembersPreviewFrame(
        GroupMembersContent.Ready(
            items = previewMembers().take(4),
            nextCursor = null,
            generation = 2,
        ),
    )
}

@Preview(name = "멤버 · 다음 페이지 로딩", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersLoadingMorePreview() {
    GroupMembersPreviewFrame(
        GroupMembersContent.Ready(
            items = previewMembers().take(4),
            nextCursor = "demo:members:4",
            generation = 1,
            isLoadingMore = true,
        ),
    )
}

@Preview(name = "멤버 · 다음 페이지 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersLoadMoreFailurePreview() {
    GroupMembersPreviewFrame(
        GroupMembersContent.Ready(
            items = previewMembers().take(4),
            nextCursor = "demo:members:4",
            generation = 1,
            loadMoreFailed = true,
        ),
    )
}

@Preview(name = "멤버 · 빈 목록", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersEmptyPreview() {
    GroupMembersPreviewFrame(GroupMembersContent.Empty)
}

@Preview(name = "멤버 · 첫 조회 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersFailurePreview() {
    GroupMembersPreviewFrame(GroupMembersContent.Failed)
}

@Preview(name = "멤버 · 권한 상실", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersForbiddenPreview() {
    GroupMembersPreviewFrame(GroupMembersContent.Forbidden)
}

@Preview(name = "멤버 · 그룹 없음", widthDp = 360, heightDp = 800)
@Composable
private fun GroupMembersNotFoundPreview() {
    GroupMembersPreviewFrame(GroupMembersContent.NotFound)
}

@Composable
private fun GroupMembersPreviewFrame(content: GroupMembersContent) {
    AppTheme {
        GroupMembersScreen(
            uiState = GroupMembersUiState(content),
            onBack = {},
            onRetry = {},
            onLoadMore = {},
            modifier = Modifier,
        )
    }
}

private fun previewMembers() =
    listOf(
        GroupMemberUiModel("산책하는 하루", GroupRole.MEMBER, "2026-09-01T08:00:00Z"),
        GroupMemberUiModel(null, GroupRole.MEMBER, "2026-09-02T08:00:00Z"),
        GroupMemberUiModel("모두의 기록", GroupRole.OWNER, "2026-09-03T08:00:00Z"),
        GroupMemberUiModel("같은 닉네임", GroupRole.MEMBER, "2026-09-04T08:00:00Z"),
        GroupMemberUiModel("같은 닉네임", GroupRole.MEMBER, "2026-09-05T08:00:00Z"),
        GroupMemberUiModel(" ", GroupRole.MEMBER, "2026-09-06T08:00:00Z"),
        GroupMemberUiModel("오늘도 천천히 걷고 감정을 기록하는 긴 닉네임 예시입니다", GroupRole.MEMBER, "2026-09-07T08:00:00Z"),
    )

@Preview(name = "모션 · 멤버 페이지 실패→재시도→완료", widthDp = 360, heightDp = 800)
@Composable
private fun MembersRetryMotionPreview() {
    var content by remember {
        mutableStateOf(
            GroupMembersContent.Ready(
                items = previewMembers().take(4),
                nextCursor = "demo:4",
                generation = 1,
                loadMoreFailed = true,
            ),
        )
    }
    val scope = rememberCoroutineScope()
    AppTheme {
        GroupMembersScreen(
            uiState = GroupMembersUiState(content),
            onBack = {},
            onLoadMore = {},
            onRetry = {
                scope.launch {
                    content = content.copy(isLoadingMore = true, loadMoreFailed = false)
                    delay(700)
                    content = content.copy(items = previewMembers(), nextCursor = null, isLoadingMore = false)
                }
            },
        )
    }
}
