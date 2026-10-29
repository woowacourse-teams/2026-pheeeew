package com.pheeeew.feature.screens.ranking.press

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.feature.emotion.component.rememberTapReducedMotion
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.screens.press.measurement.pressRaceDrawMeasurement
import com.pheeeew.feature.screens.ranking.press.components.PressRaceRow
import com.pheeeew.feature.screens.ranking.press.components.rememberRankingEncouragementMessage
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_back
import pheeeew.shared.generated.resources.press_daily_ranking_back
import pheeeew.shared.generated.resources.press_daily_ranking_empty
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
        modifier = modifier.pressRaceDrawMeasurement(uiState.frameKey, viewModel::onFrameDrawn),
    )
}

@Composable
internal fun DailyPressRankingRoute(
    date: String,
    sessionGeneration: Long?,
    refreshRevision: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DailyPressRankingViewModel =
        assistedMetroViewModel<DailyPressRankingViewModel, DailyPressRankingViewModel.Factory> {
            create(date, sessionGeneration)
        }
    LaunchedEffect(viewModel, sessionGeneration) { viewModel.onSessionChanged(sessionGeneration) }
    LaunchedEffect(viewModel) { viewModel.onResumed() }
    DisposableEffect(viewModel) { onDispose { viewModel.onRouteHidden() } }
    LaunchedEffect(viewModel, refreshRevision) {
        if (refreshRevision > 0L) viewModel.onNotificationReceived()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PressRankingScreen(
        onBack = onBack,
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
        dailyDate = date,
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
    dailyDate: String? = null,
) {
    val encouragementMessage = rememberRankingEncouragementMessage()
    val listState = remember(uiState.date) { LazyListState() }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberTapReducedMotion() || scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
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
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = backLabel,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.press_daily_ranking_title),
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (dailyDate != null) {
                    Text(
                        dailyDate,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF767D78),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.size(48.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxHeight < 600.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp)) {
                PressRankingOverview(
                    uiState,
                    dailyDate,
                    encouragementMessage,
                    rankWidth,
                    countWidth,
                    onBack,
                    onRetry,
                    onRefresh,
                    compact,
                )
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    ) {
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
                                }) { _, row ->
                                    PressRaceRow(
                                        row,
                                        rankWidth,
                                        countWidth,
                                        Modifier.animateItem(
                                            fadeInSpec = if (reducedMotion) null else tween(150),
                                            placementSpec =
                                                if (reducedMotion) {
                                                    null
                                                } else {
                                                    tween(
                                                        300,
                                                        easing = CubicBezierEasing(0.22f, 0.7f, 0.25f, 1f),
                                                    )
                                                },
                                            fadeOutSpec = if (reducedMotion) null else tween(150),
                                        ),
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.navigationBarsPadding()) }
                    }
                }
            }
        }
    }
}
