package com.pheeeew.feature.screens.ranking.press

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import com.pheeeew.core.di.createPressRankingViewModel
import com.pheeeew.core.network.ApiClient
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.screens.ranking.components.RankingTopBarHeight
import com.pheeeew.feature.screens.ranking.components.RankingTopBarOverlay
import com.pheeeew.feature.screens.ranking.components.WeekSelector
import com.pheeeew.feature.screens.ranking.components.rememberRankingTopBarScrollBehavior
import com.pheeeew.feature.screens.ranking.press.components.MyPressRankList
import com.pheeeew.feature.screens.ranking.press.components.PressEmotionFilter
import com.pheeeew.feature.screens.ranking.press.components.PressGroupRankList
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ranking_empty_week
import pheeeew.shared.generated.resources.ranking_press_title

@Composable
fun PressRankingRoute(
    apiClient: ApiClient,
    modifier: Modifier = Modifier,
    onRefreshActionChanged: ((() -> Unit)?) -> Unit = {},
) {
    val viewModel: PressRankingViewModel = viewModel { createPressRankingViewModel(apiClient) }
    val currentOnRefreshActionChanged by rememberUpdatedState(onRefreshActionChanged)
    DisposableEffect(viewModel) {
        currentOnRefreshActionChanged(viewModel::onRefresh)
        onDispose { currentOnRefreshActionChanged(null) }
    }
    ProductScreen(viewModel.telemetry, true, "press_ranking_viewed")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PressRankingScreen(
        uiState = uiState,
        onEmotionSelected = viewModel::onEmotionSelected,
        onPreviousWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
    )
}

@Composable
internal fun PressRankingScreen(
    uiState: PressRankingUiState,
    onEmotionSelected: (PressEmotion) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    val scrollState = rememberScrollState()
    val topBarBehavior = rememberRankingTopBarScrollBehavior()
    LaunchedEffect(uiState.isRefreshing, uiState.status) {
        if (uiState.isRefreshing || uiState.status == PressRankingStatus.Loading) {
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
            enabled = uiState.status != PressRankingStatus.Loading,
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
                        canGoPrevious = uiState.status == PressRankingStatus.Ready && uiState.hasPrevious,
                        canGoNext = uiState.status == PressRankingStatus.Ready && uiState.weeksAgo > 0,
                    )
                    Spacer(Modifier.height(18.dp))
                    PressEmotionFilter(
                        selectedEmotion = uiState.selectedEmotion,
                        onEmotionSelected = onEmotionSelected,
                        modifier = Modifier.padding(start = 24.dp),
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
                        PressRankingStatus.Loading -> {
                            Spacer(Modifier.height(48.dp))
                            CircularLoadingIndicator(color = AppColors.Primary)
                        }

                        PressRankingStatus.Failed -> {
                            LoadErrorContent(
                                onRetry = onRetry,
                                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 96.dp),
                            )
                        }

                        PressRankingStatus.Ready -> {
                            val myGroups = uiState.groups.filter(PressGroupRank::isMyGroup)
                            if (myGroups.isNotEmpty()) {
                                MyPressRankList(
                                    groups = myGroups,
                                    emotion = uiState.selectedEmotion,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                            if (uiState.groups.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.ranking_empty_week),
                                    color = AppColors.RankingSecondaryContent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                                )
                            } else {
                                PressGroupRankList(
                                    groups = uiState.groups,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Spacer(Modifier.navigationBarsPadding().height(AppBottomNavigationBarOverlaySpace))
                }
                RankingTopBarOverlay(
                    title = stringResource(Res.string.ranking_press_title),
                    behavior = topBarBehavior,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Preview(name = "프레스 주간 랭킹", widthDp = 360, heightDp = 800)
@Composable
private fun PressRankingScreenPreview() {
    AppTheme {
        PressRankingScreen(
            uiState =
                PressRankingUiState(
                    weekLabel = samplePressWeeks.last(),
                    hasPrevious = true,
                    groups = samplePressGroupRanks,
                    status = PressRankingStatus.Ready,
                ),
            onEmotionSelected = {},
            onPreviousWeek = {},
            onNextWeek = {},
            onRetry = {},
            onRefresh = {},
        )
    }
}
