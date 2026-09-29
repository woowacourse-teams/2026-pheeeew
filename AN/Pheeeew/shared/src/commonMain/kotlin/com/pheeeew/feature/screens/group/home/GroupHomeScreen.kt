package com.pheeeew.feature.screens.group.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.core.designsystem.component.RefreshErrorBanner
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.screens.group.home.component.GroupListItem
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import com.pheeeew.feature.screens.group.preview.HomeFixtures
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_home_create
import pheeeew.shared.generated.resources.group_home_empty_description
import pheeeew.shared.generated.resources.group_home_empty_illustration
import pheeeew.shared.generated.resources.group_home_empty_title
import pheeeew.shared.generated.resources.group_home_group_count
import pheeeew.shared.generated.resources.group_home_join
import pheeeew.shared.generated.resources.group_home_loading
import pheeeew.shared.generated.resources.group_home_my_groups
import pheeeew.shared.generated.resources.group_home_refresh_error
import pheeeew.shared.generated.resources.group_home_title

/** 그룹 홈의 시각 상태와 사용자 입력을 표현합니다. */
@Composable
fun GroupHomeScreen(
    uiState: GroupHomeUiState,
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onGroupClick: (GroupId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.GroupBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
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
        if (uiState.content !is GroupHomeContent.Ready) {
            Spacer(Modifier.height(16.dp))
            GroupHomeActions(onCreateClick = onCreateClick, onJoinClick = onJoinClick)
            Spacer(Modifier.height(28.dp))
        }

        when (val content = uiState.content) {
            GroupHomeContent.Loading -> {
                LoadingContent(modifier = Modifier.weight(1f))
            }

            GroupHomeContent.Empty -> {
                EmptyContent(
                    hasRefreshError = uiState.hasRefreshError,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }

            GroupHomeContent.Failed -> {
                FailedContent(onRetry = onRetry, modifier = Modifier.weight(1f))
            }

            is GroupHomeContent.Ready -> {
                GroupListContent(
                    groups = content.groups,
                    hasRefreshError = uiState.hasRefreshError,
                    onCreateClick = onCreateClick,
                    onJoinClick = onJoinClick,
                    onGroupClick = onGroupClick,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GroupHomeActions(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        GroupHomeActionButton(
            text = stringResource(Res.string.group_home_create),
            isPrimary = true,
            onClick = onCreateClick,
            modifier = Modifier.weight(1f),
        )
        GroupHomeActionButton(
            text = stringResource(Res.string.group_home_join),
            isPrimary = false,
            onClick = onJoinClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GroupHomeActionButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val buttonFont = notoSansKrFontFamily()
    Box(
        modifier =
            modifier
                .height(50.dp)
                .raisedButtonBorder(shape, interactionSource = interactionSource)
                .clip(shape)
                .background(if (isPrimary) AppColors.Primary else Color.White)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isPrimary) AppColors.TextPrimary else AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = buttonFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
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
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 103.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            EmptyGroupIllustration()
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.group_home_empty_title),
                color = AppColors.RankingContent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
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
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyGroupIllustration() {
    Image(
        painter = painterResource(Res.drawable.group_home_empty_illustration),
        contentDescription = null,
        modifier = Modifier.size(112.dp),
    )
}

@Composable
private fun FailedContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LoadErrorContent(
        onRetry = onRetry,
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 96.dp, bottom = AppBottomNavigationBarOverlaySpace),
    )
}

@Composable
private fun GroupListContent(
    groups: List<GroupSummaryUiModel>,
    hasRefreshError: Boolean,
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onGroupClick: (GroupId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AppBottomNavigationBarOverlaySpace),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            item(key = "home:actions", contentType = "actions") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 11.dp)) {
                    GroupHomeActions(onCreateClick = onCreateClick, onJoinClick = onJoinClick)
                }
            }
            item(key = "home:list-header", contentType = "list-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.group_home_my_groups),
                        color = AppColors.RankingContent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(Res.string.group_home_group_count, groups.size),
                        color = AppColors.RankingSecondaryContent,
                        fontSize = 12.sp,
                    )
                }
            }
            if (hasRefreshError) {
                item(key = "home:refresh-error", contentType = "refresh-error") {
                    RefreshErrorBanner(
                        message = stringResource(Res.string.group_home_refresh_error),
                        onRetry = onRetry,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            items(
                items = groups,
                key = { group -> "group:${group.id.value}" },
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

@Preview(name = "그룹 홈 - 조회 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeFailedPreview() {
    GroupHomeStatePreviewFrame(uiState = HomeFixtures.failedState)
}

@Preview(name = "그룹 홈 - 새로고침 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeRefreshFailedPreview() {
    GroupHomeStatePreviewFrame(uiState = HomeFixtures.refreshFailedState)
}

@Preview(name = "그룹 홈 - 빈 목록 새로고침 실패", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeEmptyRefreshFailedPreview() {
    GroupHomeStatePreviewFrame(uiState = HomeFixtures.emptyRefreshFailedState)
}

@Preview(name = "그룹 홈 - 최초 로딩", widthDp = 360, heightDp = 800)
@Composable
private fun GroupHomeLoadingPreview() {
    GroupHomeStatePreviewFrame(uiState = HomeFixtures.loadingState)
}

@Composable
private fun GroupHomeStatePreviewFrame(uiState: GroupHomeUiState) {
    AppTheme {
        GroupHomeScreen(
            uiState = uiState,
            onCreateClick = {},
            onJoinClick = {},
            onGroupClick = {},
            onRetry = {},
        )
    }
}
