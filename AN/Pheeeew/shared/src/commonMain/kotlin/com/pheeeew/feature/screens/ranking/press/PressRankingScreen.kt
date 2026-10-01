package com.pheeeew.feature.screens.ranking.press

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.screens.ranking.components.RankingTopBarHeight
import com.pheeeew.feature.screens.ranking.components.RankingTopBarOverlay
import com.pheeeew.feature.screens.ranking.components.WeekSelector
import com.pheeeew.feature.screens.ranking.components.rememberRankingTopBarScrollBehavior
import com.pheeeew.feature.screens.ranking.press.components.MyPressRankList
import com.pheeeew.feature.screens.ranking.press.components.PressEmotionFilter
import com.pheeeew.feature.screens.ranking.press.components.PressGroupRankList
import kotlinx.coroutines.delay

private const val MOCK_REFRESH_DURATION_MILLIS = 700L

@Composable
fun PressRankingScreen(modifier: Modifier = Modifier) {
    var selectedEmotion by remember { mutableStateOf(PressEmotion.All) }
    var selectedWeekIndex by remember { mutableIntStateOf(samplePressWeeks.lastIndex) }
    val week = samplePressWeeks[selectedWeekIndex]
    val groupRanks = samplePressGroupRanksByEmotion.getValue(selectedEmotion)
    val topBarBehavior = rememberRankingTopBarScrollBehavior()
    val pullState = rememberPullToRefreshState()
    val scrollState = rememberScrollState()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullDistance = with(LocalDensity.current) { 56.dp.toPx() }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            scrollState.scrollTo(0)
            topBarBehavior.show()
            delay(MOCK_REFRESH_DURATION_MILLIS)
            isRefreshing = false
        }
    }

    Box(modifier.fillMaxSize().background(AppColors.Background).statusBarsPadding()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            state = pullState,
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
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
                        week = week,
                        onPrevious = { if (selectedWeekIndex > 0) selectedWeekIndex-- },
                        onNext = { if (selectedWeekIndex < samplePressWeeks.lastIndex) selectedWeekIndex++ },
                        canGoPrevious = selectedWeekIndex > 0,
                        canGoNext = selectedWeekIndex < samplePressWeeks.lastIndex,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    PressEmotionFilter(
                        selectedEmotion = selectedEmotion,
                        onEmotionSelected = { selectedEmotion = it },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                    val myGroups = groupRanks.filter { it.isMyGroup }
                    if (myGroups.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        MyPressRankList(
                            groups = myGroups,
                            emotion = selectedEmotion,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    PressGroupRankList(
                        groups = groupRanks,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    Spacer(Modifier.navigationBarsPadding().height(AppBottomNavigationBarOverlaySpace))
                }
                RankingTopBarOverlay(
                    title = "프레스 주간 랭킹",
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
        PressRankingScreen()
    }
}
