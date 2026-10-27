package com.pheeeew.feature.screens.ranking.press

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.pheeeew.feature.screens.ranking.press.components.MyRaceRankCard
import com.pheeeew.feature.screens.ranking.press.components.PressRaceHeader
import com.pheeeew.feature.screens.ranking.press.components.PressRaceRow
import com.pheeeew.feature.screens.ranking.press.components.RankingEncouragementBanner
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_daily_ranking_back
import pheeeew.shared.generated.resources.press_daily_ranking_empty
import pheeeew.shared.generated.resources.press_daily_ranking_people
import pheeeew.shared.generated.resources.press_daily_ranking_refresh_error
import pheeeew.shared.generated.resources.press_daily_ranking_title

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
    val rankWidth = ((rows.maxOfOrNull { it.rank.toString().length } ?: 1) * 8f * fontScale).coerceIn(28f, 80f).dp
    val textMeasurer = rememberTextMeasurer()
    val countStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val countLabels = rows.map { raceCountText(it.count) }
    val backLabel = stringResource(Res.string.press_daily_ranking_back)
    val countWidth =
        with(LocalDensity.current) {
            val widestCount =
                countLabels.maxOfOrNull { label ->
                    textMeasurer.measure(label, countStyle, softWrap = false).size.width
                } ?: 0
            (widestCount.toDp() + 12.dp).coerceIn(40.dp, 140.dp)
        }
    Column(modifier.fillMaxSize().background(Color(0xFFFAFAFA)).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.press_daily_ranking_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier.align(Alignment.CenterStart).semantics {
                        contentDescription =
                            backLabel
                    },
            ) {
                Text("‹", fontSize = 36.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        RankingEncouragementBanner(Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(16.dp))
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    MyRaceRankCard(uiState.me, uiState.status, onBack, onRetry)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(Res.string.press_daily_ranking_people),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    PressRaceHeader(rankWidth, countWidth)
                }
                if (uiState.date != null) {
                    item { Text(uiState.date, fontSize = 12.sp, color = Color(0xFF767D78)) }
                }
                if (uiState.hasRefreshError) {
                    item {
                        RefreshErrorBanner(stringResource(Res.string.press_daily_ranking_refresh_error), onRefresh)
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
                            item {
                                Text(
                                    stringResource(Res.string.press_daily_ranking_empty),
                                    Modifier.padding(vertical = 48.dp),
                                )
                            }
                        }
                        itemsIndexed(rows, key = { index, row ->
                            "${uiState.date}:${row.publicId ?: "preview-$index"}"
                        }) { _, row -> PressRaceRow(row, rankWidth, countWidth) }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}
