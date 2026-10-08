package com.pheeeew.feature.screens.group.detail

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
import com.pheeeew.feature.component.emotion.EmotionActionsMenuMode
import com.pheeeew.feature.component.emotion.EmotionChatContentUiModel
import com.pheeeew.feature.component.emotion.EmotionChatItem
import com.pheeeew.feature.component.emotion.EmotionChatItemUiModel
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.screens.group.detail.model.GroupDetailReadyUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodContentUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedLoadState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodPostUiModel
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_feed_empty
import pheeeew.shared.generated.resources.group_detail_feed_error_message
import pheeeew.shared.generated.resources.group_detail_feed_initial_error_title
import pheeeew.shared.generated.resources.group_detail_feed_next_error_title
import pheeeew.shared.generated.resources.group_detail_feed_refresh_error_title
import pheeeew.shared.generated.resources.group_detail_refresh_error_pull

@Composable
internal fun GroupDetailReadyContent(
    detail: GroupDetailReadyUiModel,
    hasRefreshError: Boolean,
    onRetry: () -> Unit,
    onInviteClick: () -> Unit,
    onReactionClick: ((String, EmotionReactionType) -> Unit)?,
    onAudioClick: ((String) -> Unit)?,
    onBlockClick: ((String) -> Unit)?,
    onReportClick: ((String) -> Unit)?,
    onFeedRetry: (() -> Unit)?,
    onFeedLoadMore: (() -> Unit)?,
) {
    val feed = detail.feed
    val availableFeed = feed as? GroupMoodFeedUiState.Available
    val feedPosts = availableFeed?.posts.orEmpty()
    val loadState = availableFeed?.loadState
    val feedRetrying =
        when (feed) {
            is GroupMoodFeedUiState.LoadFailed -> {
                feed.isRetrying
            }

            is GroupMoodFeedUiState.Available -> {
                when (val state = feed.loadState) {
                    is GroupMoodFeedLoadState.RefreshFailed -> state.isRetrying
                    is GroupMoodFeedLoadState.LoadMoreFailed -> state.isRetrying
                    else -> false
                }
            }

            else -> {
                false
            }
        }
    val feedLoading =
        feed is GroupMoodFeedUiState.Loading ||
            feedRetrying ||
            loadState == GroupMoodFeedLoadState.Refreshing
    val showFeedLoading = rememberDelayedLoadingVisibility(feedLoading)
    val showLoadMore = rememberDelayedLoadingVisibility(loadState == GroupMoodFeedLoadState.LoadingMore)
    val listState = rememberLazyListState()
    val feedPostIds = feedPosts.map { it.id }
    var openMenu by remember { mutableStateOf<Pair<String, EmotionActionsMenuMode>?>(null) }
    LaunchedEffect(feedPostIds, openMenu?.first) {
        if (openMenu?.first?.let { it !in feedPostIds } == true) openMenu = null
    }
    LaunchedEffect(availableFeed?.posts?.size, availableFeed?.hasMore, loadState, onFeedLoadMore) {
        val currentFeed = availableFeed ?: return@LaunchedEffect
        if (!currentFeed.hasMore || loadState != GroupMoodFeedLoadState.Idle || onFeedLoadMore == null) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }.first { (lastVisible, totalItems) ->
            totalItems > 0 && lastVisible >= totalItems - LOAD_MORE_PREFETCH_ITEMS
        }
        onFeedLoadMore()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (hasRefreshError) {
            item(key = "refresh-error") {
                Text(
                    text = stringResource(Res.string.group_detail_refresh_error_pull),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF3F4F2))
                            .clickable(role = Role.Button, onClick = onRetry)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    color = AppColors.RankingSecondaryContent,
                    fontSize = 12.sp,
                )
            }
        }
        item(key = "profile-and-statistics") {
            GroupProfile(detail, onInviteClick = onInviteClick)
            Spacer(Modifier.height(18.dp))
            GroupStatistics(detail)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("감정 목록", color = AppColors.GroupInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("최신순", color = AppColors.TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
        if (showFeedLoading && !feedRetrying) {
            item(key = "feed-loading") {
                GroupMoodFeedLoadingSpinner(
                    Modifier.fillMaxWidth().height(64.dp),
                    indicatorSize = 24.dp,
                )
            }
        }
        when (feed) {
            GroupMoodFeedUiState.Loading -> {
                Unit
            }

            is GroupMoodFeedUiState.LoadFailed -> {
                item(key = "feed-load-error") {
                    GroupMoodFeedLoadError(
                        title = stringResource(Res.string.group_detail_feed_initial_error_title),
                        message = stringResource(Res.string.group_detail_feed_error_message),
                        hasItems = false,
                        isLoading = showFeedLoading && feedRetrying,
                        onRetry = { onFeedRetry?.invoke() },
                    )
                }
            }

            is GroupMoodFeedUiState.Available -> {
                if (feed.posts.isEmpty()) {
                    item(key = "feed-empty") {
                        EmotionListLoadError(
                            title = stringResource(Res.string.group_detail_feed_empty),
                            message = null,
                            hasItems = false,
                            onRetry = null,
                        )
                    }
                }
                when (val state = feed.loadState) {
                    GroupMoodFeedLoadState.Idle,
                    GroupMoodFeedLoadState.Refreshing,
                    GroupMoodFeedLoadState.LoadingMore,
                    is GroupMoodFeedLoadState.LoadMoreFailed,
                    -> {
                        Unit
                    }

                    is GroupMoodFeedLoadState.RefreshFailed -> {
                        item(key = "feed-refresh-error") {
                            GroupMoodFeedLoadError(
                                title = stringResource(Res.string.group_detail_feed_refresh_error_title),
                                message = stringResource(Res.string.group_detail_feed_error_message),
                                hasItems = feed.posts.isNotEmpty(),
                                isLoading = showFeedLoading && state.isRetrying,
                                onRetry = { onFeedRetry?.invoke() },
                            )
                        }
                    }
                }
                items(feed.posts, key = { it.id }) { post ->
                    EmotionChatItem(
                        item = post.toEmotionChatItemUiModel(),
                        menuMode = openMenu?.takeIf { it.first == post.id }?.second,
                        busy = post.isAudioLoading || post.isReactionBusy,
                        playing = (post.content as? GroupMoodContentUiModel.AudioContent)?.isPlaying == true,
                        audioLoading = post.isAudioLoading,
                        focused = false,
                        onOpenOnMap = null,
                        onOpenMenu = { mode -> openMenu = post.id to mode },
                        onDismissMenu = { if (openMenu?.first == post.id) openMenu = null },
                        onReact = onReactionClick?.let { callback -> { type -> callback(post.id, type) } },
                        onBlock = onBlockClick?.let { callback -> { callback(post.id) } },
                        onReport = onReportClick?.let { callback -> { callback(post.id) } },
                        onPlay = onAudioClick?.let { callback -> { callback(post.id) } },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                val loadMoreFailure = feed.loadState as? GroupMoodFeedLoadState.LoadMoreFailed
                if (loadMoreFailure != null) {
                    item(key = "feed-load-more-error") {
                        GroupMoodFeedLoadError(
                            title = stringResource(Res.string.group_detail_feed_next_error_title),
                            message = stringResource(Res.string.group_detail_feed_error_message),
                            hasItems = feed.posts.isNotEmpty(),
                            isLoading = showFeedLoading && loadMoreFailure.isRetrying,
                            onRetry = { onFeedRetry?.invoke() },
                        )
                    }
                }
                if (showLoadMore) {
                    item(key = "feed-load-more-loading") {
                        GroupMoodFeedLoadingSpinner(
                            Modifier.fillMaxWidth().height(64.dp),
                            indicatorSize = 24.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMoodFeedLoadError(
    title: String,
    message: String,
    hasItems: Boolean,
    isLoading: Boolean,
    onRetry: () -> Unit,
) {
    Box {
        EmotionListLoadError(
            title = title,
            message = message,
            hasItems = hasItems,
            onRetry = onRetry,
        )
        if (isLoading) {
            GroupMoodFeedLoadingSpinner(Modifier.matchParentSize(), indicatorSize = 32.dp)
        }
    }
}

@Preview(widthDp = 402, heightDp = 815, name = "그룹 상세 · 본문", showBackground = true)
@Composable
private fun GroupDetailReadyContentPreview() {
    GroupDetailReadyContent(
        detail = previewGroupDetailReadyUiModel(),
        hasRefreshError = false,
        onRetry = {},
        onInviteClick = {},
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
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = {},
    )
}

private const val LOAD_MORE_PREFETCH_ITEMS = 3

private fun GroupMoodPostUiModel.toEmotionChatItemUiModel() =
    EmotionChatItemUiModel(
        nickname = author,
        isMine = isMine,
        emotionIcon = emotionIcon,
        emotionDescription = emotionDescription,
        timeLabel = timeAgo,
        content =
            when (val mood = content) {
                is GroupMoodContentUiModel.TextContent -> {
                    EmotionChatContentUiModel.Text(mood.text)
                }

                is GroupMoodContentUiModel.AudioContent -> {
                    EmotionChatContentUiModel.Audio(mood.durationLabel)
                }
            },
        reactions = reactions,
        stamp = stamp,
    )

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
        onReactionClick = null,
        onAudioClick = null,
        onBlockClick = null,
        onReportClick = null,
        onFeedRetry = {},
        onFeedLoadMore = null,
    )
}
