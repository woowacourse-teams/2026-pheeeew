package com.pheeeew.feature.screens.group.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.core.designsystem.component.RefreshErrorBanner
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.screens.group.component.GroupListSkeleton
import com.pheeeew.feature.screens.group.home.component.GroupCatalogCard
import com.pheeeew.feature.screens.group.home.component.GroupHomeFloatingActions
import com.pheeeew.feature.screens.group.home.component.GroupHomeSwipePager
import com.pheeeew.feature.screens.group.home.component.GroupListItem
import com.pheeeew.feature.screens.group.home.explore.GroupExploreContent
import com.pheeeew.feature.screens.group.home.explore.GroupExploreUiState
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_explore_all_groups
import pheeeew.shared.generated.resources.group_explore_empty
import pheeeew.shared.generated.resources.group_explore_empty_search
import pheeeew.shared.generated.resources.group_explore_load_error
import pheeeew.shared.generated.resources.group_explore_load_more_error
import pheeeew.shared.generated.resources.group_explore_no_results
import pheeeew.shared.generated.resources.group_explore_query_hint
import pheeeew.shared.generated.resources.group_explore_recent_sort
import pheeeew.shared.generated.resources.group_explore_retry
import pheeeew.shared.generated.resources.group_home_empty_description
import pheeeew.shared.generated.resources.group_home_empty_illustration
import pheeeew.shared.generated.resources.group_home_empty_title
import pheeeew.shared.generated.resources.group_home_group_count
import pheeeew.shared.generated.resources.group_home_joined_groups
import pheeeew.shared.generated.resources.group_home_loading
import pheeeew.shared.generated.resources.group_home_refresh_error
import pheeeew.shared.generated.resources.group_home_title

@Composable
fun GroupHomeScreen(
    uiState: GroupHomeUiState,
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onGroupClick: (GroupId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: GroupHomeTab = GroupHomeTab.MyGroups,
    exploreUiState: GroupExploreUiState = GroupExploreUiState(),
    onTabSelected: (GroupHomeTab) -> Unit = {},
    onExploreQueryChanged: (String) -> Unit = {},
    onExploreSearchSubmitted: () -> Unit = {},
    onLoadMoreExploreGroups: () -> Unit = {},
    onRetryExploreGroups: () -> Unit = {},
) {
    val myGroupsListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()

    Box(modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(AppColors.GroupBackground)
                    .statusBarsPadding(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.group_home_title),
                    color = AppColors.GroupInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            GroupHomeSwipePager(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier.weight(1f),
            ) { tab ->
                when (tab) {
                    GroupHomeTab.MyGroups -> {
                        Column(Modifier.fillMaxSize()) {
                            if (uiState.isRefreshing) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularLoadingIndicator(modifier = Modifier.size(19.dp), color = AppColors.Primary)
                                }
                            }
                            MyGroupsList(
                                uiState = uiState,
                                listState = myGroupsListState,
                                onGroupClick = onGroupClick,
                                onRetry = onRetry,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    GroupHomeTab.Explore -> {
                        Column(Modifier.fillMaxSize()) {
                            if ((exploreUiState.content as? GroupExploreContent.Ready)?.isRefreshing == true) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularLoadingIndicator(modifier = Modifier.size(19.dp), color = AppColors.Primary)
                                }
                            }
                            ExploreGroupsList(
                                uiState = exploreUiState,
                                listState = exploreListState,
                                onQueryChanged = onExploreQueryChanged,
                                onSearchSubmitted = onExploreSearchSubmitted,
                                onGroupClick = onGroupClick,
                                onLoadMore = onLoadMoreExploreGroups,
                                onRetry = onRetryExploreGroups,
                                isActive = selectedTab == GroupHomeTab.Explore,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        GroupHomeFloatingActions(
            onCreateClick = onCreateClick,
            onJoinClick = onJoinClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MyGroupsList(
    uiState: GroupHomeUiState,
    listState: LazyListState,
    onGroupClick: (GroupId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 16.dp, bottom = AppBottomNavigationBarOverlaySpace + 76.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "my-groups:heading", contentType = "heading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.group_home_joined_groups),
                    color = AppColors.RankingContent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (uiState.content is GroupHomeContent.Ready) {
                    Text(
                        text = stringResource(Res.string.group_home_group_count, uiState.content.groups.size),
                        color = AppColors.RankingSecondaryContent,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        when (val content = uiState.content) {
            GroupHomeContent.Loading -> {
                item(key = "my-groups:loading") {
                    GroupListSkeleton(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }

            GroupHomeContent.Empty -> {
                item(key = "my-groups:empty") {
                    EmptyContent(
                        hasRefreshError = uiState.hasRefreshError,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                    )
                }
            }

            GroupHomeContent.Failed -> {
                item(key = "my-groups:failed") {
                    LoadErrorContent(onRetry = onRetry, modifier = Modifier.fillMaxWidth().height(300.dp))
                }
            }

            is GroupHomeContent.Ready -> {
                if (uiState.hasRefreshError) {
                    item(key = "my-groups:refresh-error") {
                        RefreshErrorBanner(
                            message = stringResource(Res.string.group_home_refresh_error),
                            onRetry = onRetry,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                items(
                    items = content.groups,
                    key = { group -> "my-group:${group.id.value}" },
                    contentType = { "group-row" },
                ) { group ->
                    GroupListItem(
                        group = group,
                        onClick = { onGroupClick(group.id) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreGroupsList(
    uiState: GroupExploreUiState,
    listState: LazyListState,
    onQueryChanged: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onGroupClick: (GroupId) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val content = uiState.content
    val canLoadMore =
        isActive &&
            content is GroupExploreContent.Ready &&
            content.hasNextPage &&
            !content.isLoadingMore &&
            !content.loadMoreFailed
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            val itemCount = listState.layoutInfo.totalItemsCount
            if (lastVisibleIndex != null && itemCount > 0 && lastVisibleIndex >= itemCount - 2) {
                currentOnLoadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 16.dp, bottom = AppBottomNavigationBarOverlaySpace + 76.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(key = "explore:search", contentType = "search") {
            GroupExploreSearchField(
                query = uiState.query,
                onQueryChanged = onQueryChanged,
                onSearchSubmitted = onSearchSubmitted,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        item(key = "explore:heading", contentType = "heading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 3.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.group_explore_all_groups),
                    color = AppColors.RankingSecondaryContent,
                    fontSize = 12.sp,
                )
                Text(
                    text = stringResource(Res.string.group_explore_recent_sort),
                    color = AppColors.RankingSecondaryContent,
                    fontSize = 12.sp,
                )
            }
        }
        when (content) {
            GroupExploreContent.NotLoaded,
            GroupExploreContent.Loading,
            -> {
                item(key = "explore:loading") {
                    GroupListSkeleton(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }

            GroupExploreContent.Failed -> {
                item(key = "explore:failed") {
                    ExploreMessage(
                        message = stringResource(Res.string.group_explore_load_error),
                        actionLabel = stringResource(Res.string.group_explore_retry),
                        onAction = onRetry,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            }

            GroupExploreContent.Empty -> {
                item(key = "explore:empty") {
                    val hasKeyword = uiState.query.isNotBlank()
                    ExploreMessage(
                        message =
                            stringResource(
                                if (hasKeyword) Res.string.group_explore_no_results else Res.string.group_explore_empty,
                            ),
                        actionLabel =
                            if (hasKeyword) {
                                stringResource(Res.string.group_explore_empty_search)
                            } else {
                                null
                            },
                        onAction = { onQueryChanged("") },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            }

            is GroupExploreContent.Ready -> {
                if (content.refreshFailed) {
                    item(key = "explore:refresh-error") {
                        RefreshErrorBanner(
                            message = stringResource(Res.string.group_explore_load_error),
                            onRetry = onRetry,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                items(
                    items = content.items,
                    key = { item -> "explore-group:${item.group.id.value}" },
                    contentType = { "explore-group-row" },
                ) { item ->
                    GroupCatalogCard(
                        group = item.group,
                        joinType = item.joinType,
                        isJoined = item.isJoined,
                        onClick = { onGroupClick(item.group.id) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                if (content.isLoadingMore) {
                    item(key = "explore:loading-more") {
                        Box(modifier = Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                            CircularLoadingIndicator(modifier = Modifier.size(20.dp), color = AppColors.Primary)
                        }
                    }
                } else if (content.loadMoreFailed) {
                    item(key = "explore:load-more-error") {
                        ExploreMessage(
                            message = stringResource(Res.string.group_explore_load_more_error),
                            actionLabel = stringResource(Res.string.group_explore_retry),
                            onAction = onRetry,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(72.dp),
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupExploreSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(
                    48.dp,
                ).clip(shape)
                .background(Color(0xFFEEEEEC))
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 1.6.dp.toPx()
            drawCircle(
                color = AppColors.GroupInk,
                radius = size.minDimension * 0.32f,
                center =
                    androidx.compose.ui.geometry
                        .Offset(size.width * 0.42f, size.height * 0.42f),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = AppColors.GroupInk,
                start =
                    androidx.compose.ui.geometry
                        .Offset(size.width * 0.65f, size.height * 0.65f),
                end =
                    androidx.compose.ui.geometry
                        .Offset(size.width * 0.91f, size.height * 0.91f),
                strokeWidth = stroke,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = AppColors.GroupInk, fontSize = 14.sp),
            cursorBrush = SolidColor(AppColors.GroupInk),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        onSearchSubmitted()
                    },
                ),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.group_explore_query_hint),
                            color = AppColors.RankingSecondaryContent,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        val clearInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = clearInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { if (query.isNotEmpty()) onQueryChanged("") },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", color = AppColors.GroupInk, fontSize = 20.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularLoadingIndicator(color = AppColors.Primary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.group_home_loading),
            color = AppColors.RankingSecondaryContent,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun EmptyContent(
    hasRefreshError: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.group_home_empty_illustration),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(Res.string.group_home_empty_title),
                color = AppColors.RankingContent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.group_home_empty_description),
                color = AppColors.RankingSecondaryContent,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (hasRefreshError) {
            RefreshErrorBanner(
                message = stringResource(Res.string.group_home_refresh_error),
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ExploreMessage(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.padding(horizontal = if (compact) 8.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = AppColors.RankingSecondaryContent,
            fontSize = if (compact) 12.sp else 14.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
            Text(
                text = actionLabel,
                modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(6.dp),
                color = AppColors.GroupInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
