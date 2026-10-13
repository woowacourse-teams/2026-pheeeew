package com.pheeeew.feature.screens.group.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.core.designsystem.component.RefreshErrorBanner
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.navigation.FlowBackHandler
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.screens.group.component.GroupListSkeleton
import com.pheeeew.feature.screens.group.home.component.GroupCatalogCard
import com.pheeeew.feature.screens.group.home.component.GroupHomeFloatingActions
import com.pheeeew.feature.screens.group.home.component.GroupHomeSwipePager
import com.pheeeew.feature.screens.group.home.component.GroupListItem
import com.pheeeew.feature.screens.group.home.component.GroupSearchHeader
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
import pheeeew.shared.generated.resources.group_explore_recent_sort
import pheeeew.shared.generated.resources.group_explore_retry
import pheeeew.shared.generated.resources.group_home_empty_description
import pheeeew.shared.generated.resources.group_home_empty_illustration
import pheeeew.shared.generated.resources.group_home_empty_title
import pheeeew.shared.generated.resources.group_home_group_count
import pheeeew.shared.generated.resources.group_home_joined_groups
import pheeeew.shared.generated.resources.group_home_loading
import pheeeew.shared.generated.resources.group_home_refresh_error

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
    initialSearchOpen: Boolean = false,
) {
    val myGroupsListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    var searchOpen by rememberSaveable { mutableStateOf(initialSearchOpen) }
    var recentQueries by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun recordSearch() {
        val keyword = exploreUiState.query.trim()
        if (keyword.isNotEmpty()) {
            recentQueries = (listOf(keyword) + recentQueries.filterNot { it.equals(keyword, true) }).take(5)
        }
    }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current

    fun closeSearch() {
        recordSearch()
        searchOpen = false
        focusManager.clearFocus()
    }
    FlowBackHandler(searchOpen) { closeSearch() }
    LaunchedEffect(selectedTab) {
        if (selectedTab == GroupHomeTab.MyGroups) {
            searchOpen = false
            focusManager.clearFocus()
        }
    }

    Box(modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(AppColors.GroupBackground)
                    .statusBarsPadding(),
        ) {
            GroupHomeSwipePager(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier.weight(1f),
                isSearchOpen = searchOpen,
                headerContent = { tabs ->
                    GroupSearchHeader(
                        isOpen = searchOpen,
                        query = exploreUiState.query,
                        recentQueries = recentQueries,
                        showSummary = selectedTab == GroupHomeTab.Explore,
                        onOpen = {
                            onTabSelected(GroupHomeTab.Explore)
                            searchOpen = true
                        },
                        onClose = { closeSearch() },
                        onQueryChanged = onExploreQueryChanged,
                        onSubmit = {
                            onExploreSearchSubmitted()
                            closeSearch()
                        },
                        tabsContent = tabs,
                    )
                },
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
                                onGroupClick = { id ->
                                    closeSearch()
                                    onGroupClick(id)
                                },
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
        if (!keyboardVisible && !searchOpen) {
            GroupHomeFloatingActions(
                onCreateClick = onCreateClick,
                onJoinClick = onJoinClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
                            modifier = Modifier.animateItem(placementSpec = null).padding(horizontal = 24.dp),
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
                        modifier = Modifier.animateItem(placementSpec = null).padding(horizontal = 24.dp),
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
    onGroupClick: (GroupId) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    var lastResults by remember { mutableStateOf<GroupExploreContent.Ready?>(null) }
    LaunchedEffect(uiState.content) {
        lastResults =
            when (val latest = uiState.content) {
                is GroupExploreContent.Ready -> latest
                GroupExploreContent.Loading -> lastResults
                else -> null
            }
    }
    val searching = uiState.content == GroupExploreContent.Loading && lastResults != null
    val content =
        if (searching) {
            lastResults!!.copy(
                nextCursor = null,
                isLoadingMore = false,
                loadMoreFailed = false,
                refreshFailed = false,
            )
        } else {
            uiState.content
        }
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
                if (searching) {
                    CircularLoadingIndicator(Modifier.size(16.dp), color = AppColors.TextSecondary)
                } else {
                    Text(
                        text = stringResource(Res.string.group_explore_recent_sort),
                        color = AppColors.RankingSecondaryContent,
                        fontSize = 12.sp,
                    )
                }
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
                            modifier = Modifier.animateItem(placementSpec = null).padding(horizontal = 24.dp),
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
                        enabled = !searching,
                        highlightQuery = if (searching) "" else uiState.query,
                        modifier = Modifier.animateItem(placementSpec = null).padding(horizontal = 24.dp),
                    )
                }
                if (content.isLoadingMore) {
                    item(key = "explore:loading-more") {
                        Box(
                            modifier = Modifier.animateItem().fillMaxWidth().height(52.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularLoadingIndicator(modifier = Modifier.size(20.dp), color = AppColors.Primary)
                        }
                    }
                } else if (content.loadMoreFailed) {
                    item(key = "explore:load-more-error") {
                        ExploreMessage(
                            message = stringResource(Res.string.group_explore_load_more_error),
                            actionLabel = stringResource(Res.string.group_explore_retry),
                            onAction = onRetry,
                            modifier =
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .height(72.dp),
                            compact = true,
                        )
                    }
                }
            }
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
