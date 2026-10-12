package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.emotion.EmotionActionsMenuMode
import com.pheeeew.feature.component.emotion.EmotionChatContentUiModel
import com.pheeeew.feature.component.emotion.EmotionChatItem
import com.pheeeew.feature.component.emotion.EmotionChatItemUiModel
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinUiState
import com.pheeeew.feature.screens.group.detail.GroupDetailReadyContent
import com.pheeeew.feature.screens.group.detail.model.GroupDetailReadyUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodContentUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedLoadState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodPostUiModel
import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.feature.screens.group.preview.previewGroupDetailReadyUiModel
import com.pheeeew.feature.screens.group.preview.previewGroupMoodPosts
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_feed_empty
import pheeeew.shared.generated.resources.group_detail_feed_error_message
import pheeeew.shared.generated.resources.group_detail_feed_initial_error_title
import pheeeew.shared.generated.resources.group_detail_feed_next_error_title
import pheeeew.shared.generated.resources.group_detail_feed_refresh_error_title
import pheeeew.shared.generated.resources.group_detail_refresh_error_pull

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 본문", showBackground = true)
@Composable
private fun GroupDetailReadyContentPreview() {
    GroupDetailReadyContent(
        detail = previewGroupDetailReadyUiModel(),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = null,
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 목록 오류", showBackground = true)
@Composable
private fun GroupDetailReadyContentFeedErrorPreview() {
    GroupDetailReadyContent(
        detail = previewGroupDetailReadyUiModel().copy(feed = GroupMoodFeedUiState.LoadFailed(isRetrying = false)),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = null,
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 목록 로딩", showBackground = true)
@Composable
private fun GroupMoodFeedLoadingPreview() {
    GroupDetailReadyContent(
        detail = previewGroupDetailReadyUiModel().copy(feed = GroupMoodFeedUiState.Loading),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 목록 조회 실패", showBackground = true)
@Composable
private fun GroupMoodFeedErrorPreview() {
    GroupDetailReadyContent(
        detail = previewGroupDetailReadyUiModel().copy(feed = GroupMoodFeedUiState.LoadFailed(isRetrying = false)),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 목록 재시도 중", showBackground = true)
@Composable
private fun GroupMoodFeedRetryingPreview() {
    GroupDetailReadyContent(
        detail = previewGroupDetailReadyUiModel().copy(feed = GroupMoodFeedUiState.LoadFailed(isRetrying = true)),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 새로고침 실패", showBackground = true)
@Composable
private fun GroupMoodFeedRefreshErrorPreview() {
    GroupDetailReadyContent(
        detail =
            previewGroupDetailReadyUiModel().copy(
                feed =
                    GroupMoodFeedUiState.Available(
                        posts = previewGroupMoodPosts(),
                        hasMore = true,
                        loadState = GroupMoodFeedLoadState.RefreshFailed(isRetrying = false),
                    ),
            ),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 새로고침 중", showBackground = true)
@Composable
private fun GroupMoodFeedRefreshingPreview() {
    GroupDetailReadyContent(
        detail =
            previewGroupDetailReadyUiModel().copy(
                feed =
                    GroupMoodFeedUiState.Available(
                        posts = previewGroupMoodPosts(),
                        hasMore = true,
                        loadState = GroupMoodFeedLoadState.Refreshing,
                    ),
            ),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 추가 조회 실패", showBackground = true)
@Composable
private fun GroupMoodFeedLoadMoreErrorPreview() {
    GroupDetailReadyContent(
        detail =
            previewGroupDetailReadyUiModel().copy(
                feed =
                    GroupMoodFeedUiState.Available(
                        posts = previewGroupMoodPosts(),
                        hasMore = true,
                        loadState = GroupMoodFeedLoadState.LoadMoreFailed(isRetrying = false),
                    ),
            ),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 추가 조회 중", showBackground = true)
@Composable
private fun GroupMoodFeedLoadingMorePreview() {
    GroupDetailReadyContent(
        detail =
            previewGroupDetailReadyUiModel().copy(
                feed =
                    GroupMoodFeedUiState.Available(
                        posts = previewGroupMoodPosts(),
                        hasMore = true,
                        loadState = GroupMoodFeedLoadState.LoadingMore,
                    ),
            ),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 빈 목록", showBackground = true)
@Composable
private fun GroupMoodFeedEmptyPreview() {
    GroupDetailReadyContent(
        detail =
            previewGroupDetailReadyUiModel().copy(
                feed =
                    GroupMoodFeedUiState.Available(
                        posts = emptyList(),
                        hasMore = false,
                        loadState = GroupMoodFeedLoadState.Idle,
                    ),
            ),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
        onJoinClick = {},
        joinState = GroupDetailJoinUiState(),
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = null,
    )
}
