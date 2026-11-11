package com.pheeeew.feature.screens.pressevent

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.RaisedCardButton
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appMutedText
import com.pheeeew.core.designsystem.theme.appPageBackground
import com.pheeeew.core.designsystem.theme.appPrimary
import com.pheeeew.core.designsystem.theme.appSurface
import com.pheeeew.feature.emotion.component.rememberTapReducedMotion
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.components.PressRaceHeader
import com.pheeeew.feature.screens.ranking.press.components.PressRaceRow
import com.pheeeew.feature.screens.ranking.press.components.ProvideRaceAnimationClock
import com.pheeeew.feature.screens.ranking.press.components.raceAnimationViewport
import com.pheeeew.feature.screens.ranking.press.toRaceRows

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PressEventRankingScreen(
    state: PressEventViewState,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val motionScale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val reducedMotion = rememberTapReducedMotion() || motionScale == 0f
    val rows = remember(state.people) { state.people.toRaceRows() }
    val pullState = rememberPullToRefreshState()
    ProvideRaceAnimationClock(reducedMotion, motionScale) { animationClock ->
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.appInk,
        ) {
            Column(
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.appPageBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    EventBackButton(onBack, MaterialTheme.colorScheme.appInk, Modifier.align(Alignment.CenterStart))
                    Text("이벤트", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                PullToRefreshBox(
                    isRefreshing = state.rankingLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f),
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = state.rankingLoading,
                            modifier = Modifier.align(Alignment.TopCenter),
                            color = AppColors.Primary,
                        )
                    },
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize().raceAnimationViewport(animationClock),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item { EventLeaderCard(state.leader, reducedMotion = reducedMotion) }
                        item {
                            Text("이벤트 순위", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            state.me?.let { Text("내 최고 기록 ${it.bestScore}회 · ${it.rank}위", fontSize = 14.sp) }
                        }
                        item {
                            when {
                                !state.configured -> Text("이벤트 준비 중이에요", color = MaterialTheme.colorScheme.appMutedText)
                                state.rankingLoading -> Text("순위를 불러오고 있어요")
                                state.rankingError -> Unit
                                state.rankedAt != null && rows.isEmpty() -> Text("아직 참여 기록이 없어요. 첫 도전을 시작해 보세요!")
                                else -> Unit
                            }
                        }
                        item { PressRaceHeader(40.dp, 72.dp) }
                        if (state.configured && state.rankingError && !state.rankingLoading) {
                            item { RankingErrorContent(onRetry = onRefresh, Modifier.padding(vertical = 24.dp)) }
                        }
                        items(rows, key = { requireNotNull(it.publicId) }) { row ->
                            PressRaceRow(
                                row,
                                40.dp,
                                72.dp,
                                Modifier
                                    .animateItem(
                                        fadeInSpec = if (reducedMotion) null else tween(150),
                                        placementSpec =
                                            if (reducedMotion) {
                                                null
                                            } else {
                                                tween(300, easing = CubicBezierEasing(0.22f, 0.7f, 0.25f, 1f))
                                            },
                                        fadeOutSpec = if (reducedMotion) null else tween(150),
                                    ).background(MaterialTheme.colorScheme.appSurface, AppShapes.Action),
                                reducedMotion = reducedMotion,
                            )
                        }
                        if (state.nextCursor != null) {
                            item {
                                TextButton(
                                    onClick = onLoadMore,
                                    enabled = !state.loadingMore && !state.rankingLoading,
                                ) {
                                    Text(
                                        if (state.loadingMore) {
                                            "불러오는 중…"
                                        } else if (state.nextPageError) {
                                            "다음 순위 다시 불러오기"
                                        } else {
                                            "더 보기"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                RaisedCardButton(
                    backgroundColor = MaterialTheme.colorScheme.appPrimary,
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp),
                ) {
                    Text(
                        "시작하기",
                        Modifier.align(Alignment.Center),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.appInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingErrorContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.appInk
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("순위를 불러오지 못했어요", color = ink, fontSize = 14.sp)
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = Color.White),
        ) {
            Text("다시시도", fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(name = "이벤트 순위", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventRankingPreview() {
    val leader = PressPersonRank(1L, "한숨많은곰", 210L, "sample")
    AppTheme {
        PressEventRankingScreen(PressEventViewState(configured = true, leader = leader, people = listOf(leader)), {
        }, {}, {}, {})
    }
}

@Preview(name = "이벤트 순위 빈 상태", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventEmptyRankingPreview() {
    AppTheme { PressEventRankingScreen(PressEventViewState(), {}, {}, {}, {}) }
}

@Preview(name = "이벤트 순위 불러오기 실패", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventRankingErrorPreview() {
    AppTheme { PressEventRankingScreen(PressEventViewState(configured = true, rankingError = true), {}, {}, {}, {}) }
}
