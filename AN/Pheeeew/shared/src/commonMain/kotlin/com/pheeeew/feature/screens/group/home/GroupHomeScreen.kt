package com.pheeeew.feature.screens.group.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.home.component.GroupListItem
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_home_create
import pheeeew.shared.generated.resources.group_home_empty_description
import pheeeew.shared.generated.resources.group_home_empty_illustration
import pheeeew.shared.generated.resources.group_home_empty_title
import pheeeew.shared.generated.resources.group_home_error_description
import pheeeew.shared.generated.resources.group_home_error_illustration
import pheeeew.shared.generated.resources.group_home_error_title
import pheeeew.shared.generated.resources.group_home_group_count
import pheeeew.shared.generated.resources.group_home_join
import pheeeew.shared.generated.resources.group_home_loading
import pheeeew.shared.generated.resources.group_home_my_groups
import pheeeew.shared.generated.resources.group_home_refresh_error
import pheeeew.shared.generated.resources.group_home_retry
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
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(Res.string.group_home_title),
            modifier = Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 38.dp),
            color = AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
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
        Spacer(Modifier.height(28.dp))

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
                    isRefreshing = uiState.isRefreshing,
                    hasRefreshError = uiState.hasRefreshError,
                    onGroupClick = onGroupClick,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GroupHomeActionButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(25.dp)
    Box(
        modifier =
            modifier
                .height(50.dp)
                .clip(shape)
                .background(if (isPrimary) AppColors.GroupInk else Color.White)
                .then(if (isPrimary) Modifier else Modifier.border(1.dp, AppColors.GroupInk, shape))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isPrimary) Color.White else AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
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
        CircularProgressIndicator(color = AppColors.RankingAccent)
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
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Image(
            painter = painterResource(Res.drawable.group_home_error_illustration),
            contentDescription = null,
            modifier = Modifier.size(width = 138.dp, height = 116.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.group_home_error_title),
            color = AppColors.RankingContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(19.dp))
        Text(
            text = stringResource(Res.string.group_home_error_description),
            color = AppColors.RankingSecondaryContent,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(29.dp))
        GroupHomeActionButton(
            text = stringResource(Res.string.group_home_retry),
            isPrimary = false,
            onClick = onRetry,
            modifier = Modifier.width(218.dp),
        )
    }
}

@Composable
private fun GroupListContent(
    groups: List<GroupSummaryUiModel>,
    isRefreshing: Boolean,
    hasRefreshError: Boolean,
    onGroupClick: (GroupId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.RankingAccent,
                trackColor = AppColors.RankingSurface,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            item(key = "home:list-header", contentType = "list-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
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
                    RefreshErrorBanner(onRetry = onRetry)
                }
            }
            items(
                items = groups,
                key = { group -> "group:${group.id.value}" },
                contentType = { "group-row" },
            ) { group ->
                GroupListItem(group = group, onClick = { onGroupClick(group.id) })
            }
        }
    }
}

@Composable
private fun RefreshErrorBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.RankingSurface)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.group_home_refresh_error),
            modifier = Modifier.weight(1f),
            color = AppColors.RankingContent,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.group_home_retry),
            color = AppColors.RankingContent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}
