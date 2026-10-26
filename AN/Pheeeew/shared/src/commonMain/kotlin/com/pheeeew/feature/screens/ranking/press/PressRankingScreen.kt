package com.pheeeew.feature.screens.ranking.press

import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.core.designsystem.component.RefreshErrorBanner
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.screens.ranking.press.components.PressRaceHeader
import com.pheeeew.feature.screens.ranking.press.components.PressRaceRow
import com.pheeeew.feature.screens.ranking.press.components.RaceFlag
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun PressRankingRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshActionChanged: ((() -> Unit)?) -> Unit = {},
) {
    val viewModel: PressRankingViewModel = metroViewModel()
    val currentOnRefreshActionChanged by rememberUpdatedState(onRefreshActionChanged)
    DisposableEffect(viewModel) {
        currentOnRefreshActionChanged(viewModel::onRefresh)
        onDispose { currentOnRefreshActionChanged(null) }
    }
    ProductScreen(viewModel.telemetry, true, "press_ranking_viewed")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PressRankingScreen(
        onBack = onBack,
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
    )
}

@Composable
internal fun PressRankingScreen(
    onBack: () -> Unit,
    uiState: PressRankingUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    rows: List<PressRaceRowModel> = uiState.people.toRaceRows(),
) {
    val fontScale = LocalDensity.current.fontScale
    val rankWidth = ((rows.maxOfOrNull { it.rank.toString().length } ?: 1) * 8f * fontScale).coerceAtLeast(28f).dp
    val textMeasurer = rememberTextMeasurer()
    val countStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val countWidth =
        with(LocalDensity.current) {
            val widestCount =
                rows.maxOfOrNull { row ->
                    textMeasurer.measure("${row.count}회", countStyle, softWrap = false).size.width
                } ?: 0
            (widestCount.toDp() + 12.dp).coerceIn(40.dp, 140.dp)
        }
    Column(modifier.fillMaxSize().background(Color(0xFFFAFAFA)).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text("전체 순위", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier.align(Alignment.CenterStart).semantics {
                        contentDescription =
                            "뒤로 가기"
                    },
            ) {
                Text("‹", fontSize = 36.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(
                    Color(0xFFF0F1EB),
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(16.dp),
                ).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RaceFlag(Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("한 번 누를 때마다 앞으로 한 칸!", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "이번 주 · ${uiState.weekLabel}",
            color = Color(0xFF767D78),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { PressRaceHeader(rankWidth, countWidth) }
                if (uiState.hasRefreshError) {
                    item {
                        RefreshErrorBanner("랭킹을 새로고침하지 못했어요.", onRefresh)
                    }
                }
                when (uiState.status) {
                    PressRankingStatus.Loading -> {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularLoadingIndicator(color = Color(0xFF202323))
                            }
                        }
                    }

                    PressRankingStatus.Failed -> {
                        item {
                            LoadErrorContent(onRetry, Modifier.padding(top = 48.dp))
                        }
                    }

                    PressRankingStatus.Ready -> {
                        if (rows.isEmpty()) {
                            item { Text("아직 참여자가 없어요.", Modifier.padding(vertical = 48.dp)) }
                        }
                        items(rows) { row -> PressRaceRow(row, rankWidth, countWidth) }
                        if (rows.none { it.isMe }) {
                            item {
                                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
                                    val me = uiState.me
                                    Text(
                                        if (me == null) "이번 주 프레스에 참여해 보세요." else "내 순위 ${me.rank}위 · ${me.count}회",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                    if (me != null) {
                                        Text("목록 내 본인 표시는 아직 제공되지 않아요.", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}
