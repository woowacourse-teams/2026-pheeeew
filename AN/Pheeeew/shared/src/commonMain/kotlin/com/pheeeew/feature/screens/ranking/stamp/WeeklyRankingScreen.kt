package com.pheeeew.feature.screens.ranking.stamp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.core.designsystem.component.RefreshErrorBanner
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.di.createWeeklyRankingViewModel
import com.pheeeew.core.network.ApiClient
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.screens.ranking.components.RankingTopBarHeight
import com.pheeeew.feature.screens.ranking.components.RankingTopBarOverlay
import com.pheeeew.feature.screens.ranking.components.WeekSelector
import com.pheeeew.feature.screens.ranking.components.rememberRankingTopBarScrollBehavior
import com.pheeeew.feature.screens.ranking.stamp.components.RankingRow
import com.pheeeew.feature.screens.ranking.stamp.components.TopThreeRanking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ranking_empty_week
import pheeeew.shared.generated.resources.ranking_stamp_title

@Composable
fun WeeklyRankingRoute(
    apiClient: ApiClient,
    onGroupClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRefreshActionChanged: ((() -> Unit)?) -> Unit = {},
) {
    val viewModel: WeeklyRankingViewModel = viewModel { createWeeklyRankingViewModel(apiClient) }
    val currentOnRefreshActionChanged by rememberUpdatedState(onRefreshActionChanged)
    DisposableEffect(viewModel) {
        currentOnRefreshActionChanged(viewModel::onRefresh)
        onDispose { currentOnRefreshActionChanged(null) }
    }
    WeeklyRankingRoute(viewModel = viewModel, onGroupClick = onGroupClick, modifier = modifier)
}

@Composable
fun WeeklyRankingRoute(
    viewModel: WeeklyRankingViewModel,
    onGroupClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProductScreen(viewModel.telemetry, true, "ranking_viewed")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyRankingScreen(
        uiState = uiState,
        onPreviousWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefresh,
        onGroupClick = onGroupClick,
        modifier = modifier,
    )
}

@Composable
fun WeeklyRankingScreen(
    uiState: WeeklyRankingUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onGroupClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    val scrollState = rememberScrollState()
    val topBarBehavior = rememberRankingTopBarScrollBehavior()
    LaunchedEffect(uiState.isRefreshing, uiState.status) {
        if (uiState.isRefreshing || uiState.status == WeeklyRankingStatus.Loading) {
            scrollState.scrollTo(0)
            topBarBehavior.show()
        }
    }
    val pullDistance = with(LocalDensity.current) { 56.dp.toPx() }
    Column(
        modifier = modifier.fillMaxSize().background(AppColors.Background).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            enabled = uiState.status != WeeklyRankingStatus.Loading,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = AppColors.Surface,
                    color = AppColors.Primary,
                )
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(topBarBehavior.nestedScrollConnection)
                            .graphicsLayer {
                                translationY = pullState.distanceFraction.coerceIn(0f, 1f) * pullDistance
                            }.verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(RankingTopBarHeight))
                    Spacer(Modifier.height(12.dp))
                    WeekSelector(
                        week = uiState.weekLabel,
                        onPrevious = onPreviousWeek,
                        onNext = onNextWeek,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        canGoPrevious = uiState.status == WeeklyRankingStatus.Ready && uiState.hasPrevious,
                        canGoNext = uiState.status == WeeklyRankingStatus.Ready && uiState.weeksAgo > 0,
                    )
                    Spacer(Modifier.height(22.dp))
                    if (uiState.hasRefreshError) {
                        RefreshErrorBanner(
                            message = "랭킹을 새로고침하지 못했어요.",
                            onRetry = onRefresh,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        )
                    }
                    when (uiState.status) {
                        WeeklyRankingStatus.Loading -> {
                            Spacer(Modifier.height(48.dp))
                            CircularLoadingIndicator(color = AppColors.Primary)
                        }

                        WeeklyRankingStatus.Failed -> {
                            LoadErrorContent(
                                onRetry = onRetry,
                                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 96.dp),
                            )
                        }

                        WeeklyRankingStatus.Ready -> {
                            if (uiState.rankings.isEmpty()) {
                                EmptyRankingContent(
                                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 96.dp),
                                )
                            } else if (uiState.rankings.size >= 3) {
                                TopThreeRanking(members = uiState.rankings.take(3), onGroupClick = onGroupClick)
                                Spacer(Modifier.height(22.dp))
                                uiState.rankings.drop(3).forEach { member ->
                                    RankingRow(
                                        member = member,
                                        onClick = { onGroupClick(member.groupId) },
                                        modifier = Modifier.padding(bottom = 14.dp),
                                    )
                                }
                            } else {
                                uiState.rankings.forEach { member ->
                                    RankingRow(
                                        member = member,
                                        onClick = { onGroupClick(member.groupId) },
                                        modifier = Modifier.padding(bottom = 14.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(AppBottomNavigationBarOverlaySpace))
                }
                RankingTopBarOverlay(
                    title = stringResource(Res.string.ranking_stamp_title),
                    behavior = topBarBehavior,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun EmptyRankingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_emotion_frustrated),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.ranking_empty_week),
            color = AppColors.RankingContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "랭킹 - 빈 목록", widthDp = 360, heightDp = 800)
@Composable
private fun WeeklyRankingEmptyPreview() {
    WeeklyRankingPreviewFrame(
        WeeklyRankingUiState(
            weekLabel = sampleWeeks[2],
            hasPrevious = true,
            status = WeeklyRankingStatus.Ready,
        ),
    )
}

@Preview(name = "랭킹 - 목록", widthDp = 360, heightDp = 800)
@Composable
private fun WeeklyRankingScreenPreview() {
    WeeklyRankingPreviewFrame(
        WeeklyRankingUiState(
            weekLabel = sampleWeeks[2],
            hasPrevious = true,
            rankings = sampleRankings,
            status = WeeklyRankingStatus.Ready,
        ),
    )
}

@Preview(name = "랭킹 - 최초 로딩", widthDp = 360, heightDp = 800)
@Composable
private fun WeeklyRankingLoadingPreview() {
    WeeklyRankingPreviewFrame(WeeklyRankingUiState(status = WeeklyRankingStatus.Loading))
}

@Preview(name = "랭킹 - 조회 실패", widthDp = 360, heightDp = 800)
@Composable
private fun WeeklyRankingFailedPreview() {
    WeeklyRankingPreviewFrame(WeeklyRankingUiState(status = WeeklyRankingStatus.Failed))
}

@Preview(name = "랭킹 - 목록 새로고침 실패", widthDp = 360, heightDp = 800)
@Composable
private fun WeeklyRankingRefreshErrorPreview() {
    WeeklyRankingPreviewFrame(
        WeeklyRankingUiState(
            weekLabel = sampleWeeks[2],
            hasPrevious = true,
            rankings = sampleRankings,
            status = WeeklyRankingStatus.Ready,
            hasRefreshError = true,
        ),
    )
}

@Preview(name = "랭킹 - 3명 미만 목록 새로고침 실패", widthDp = 360, heightDp = 800)
@Composable
private fun WeeklyRankingShortListRefreshErrorPreview() {
    WeeklyRankingPreviewFrame(
        WeeklyRankingUiState(
            weekLabel = sampleWeeks[2],
            hasPrevious = true,
            rankings = sampleRankings.take(2),
            status = WeeklyRankingStatus.Ready,
            hasRefreshError = true,
        ),
    )
}

@Preview(name = "랭킹 - 빈 목록 새로고침 실패", widthDp = 360, heightDp = 800)
@Composable
private fun WeeklyRankingEmptyRefreshErrorPreview() {
    WeeklyRankingPreviewFrame(
        WeeklyRankingUiState(
            weekLabel = sampleWeeks[2],
            hasPrevious = true,
            status = WeeklyRankingStatus.Ready,
            hasRefreshError = true,
        ),
    )
}

@Composable
private fun WeeklyRankingPreviewFrame(uiState: WeeklyRankingUiState) {
    AppTheme {
        WeeklyRankingScreen(
            uiState = uiState,
            onPreviousWeek = {},
            onNextWeek = {},
            onRetry = {},
            onRefresh = {},
            onGroupClick = {},
        )
    }
}
