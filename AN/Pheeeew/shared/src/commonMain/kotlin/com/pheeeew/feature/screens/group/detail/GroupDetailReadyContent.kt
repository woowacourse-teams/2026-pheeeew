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
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.emotion.EmotionActionsMenuMode
import com.pheeeew.feature.component.emotion.EmotionChatContentUiModel
import com.pheeeew.feature.component.emotion.EmotionChatItem
import com.pheeeew.feature.component.emotion.EmotionChatItemUiModel
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinUiState
import com.pheeeew.feature.screens.group.detail.model.GroupDetailReadyUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodContentUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedLoadState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodPostUiModel
import com.pheeeew.feature.screens.group.model.GroupJoinType
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
    onJoinClick: () -> Unit,
    joinState: GroupDetailJoinUiState,
    onReactionClick: ((String, EmotionReactionType) -> Unit)?,
    onAudioClick: ((String) -> Unit)?,
    onBlockClick: ((String) -> Unit)?,
    onReportClick: ((String) -> Unit)?,
    onFeedRetry: (() -> Unit)?,
    onFeedLoadMore: (() -> Unit)?,
    onMembersClick: (() -> Unit)? = null,
) {
    val feed = detail.feed
    val canReact = detail.role != GroupRole.NONE
    val canOpenActionsMenu = canReact || onBlockClick != null || onReportClick != null
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
            GroupProfile(
                group = detail,
                onInviteClick = onInviteClick,
                joinState = joinState,
                onJoinClick = onJoinClick,
                onMembersClick = onMembersClick,
            )
            if (detail.role == GroupRole.NONE && detail.joinType == GroupJoinType.PRIVATE &&
                joinState.phase != GroupDetailJoinPhase.VerificationRequired
            ) {
                Spacer(Modifier.height(18.dp))
                GroupDetailPrivateJoinNotice(onJoinClick = onJoinClick, phase = joinState.phase)
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(18.dp))
            }
            GroupStatistics(detail)
            Spacer(Modifier.height(24.dp))
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
                        onOpenMenu =
                            if (canOpenActionsMenu) {
                                { mode -> openMenu = post.id to mode }
                            } else {
                                null
                            },
                        onDismissMenu = { if (openMenu?.first == post.id) openMenu = null },
                        onReact =
                            if (canReact) {
                                onReactionClick?.let { callback -> { type -> callback(post.id, type) } }
                            } else {
                                null
                            },
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
