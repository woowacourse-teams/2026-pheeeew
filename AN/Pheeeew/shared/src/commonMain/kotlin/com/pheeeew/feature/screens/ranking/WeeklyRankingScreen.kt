package com.pheeeew.feature.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.di.createWeeklyRankingViewModel
import com.pheeeew.core.network.ApiClient
import com.pheeeew.feature.screens.ranking.components.RankingRow
import com.pheeeew.feature.screens.ranking.components.TopThreeRanking
import com.pheeeew.feature.screens.ranking.components.WeekSelector

@Composable
fun WeeklyRankingRoute(
    apiClient: ApiClient,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeeklyRankingViewModel = viewModel { createWeeklyRankingViewModel(apiClient) }
    WeeklyRankingRoute(viewModel = viewModel, modifier = modifier)
}

@Composable
fun WeeklyRankingRoute(
    viewModel: WeeklyRankingViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyRankingScreen(
        uiState = uiState,
        onPreviousWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun WeeklyRankingScreen(
    uiState: WeeklyRankingUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(AppColors.Background).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "주간 랭킹",
                color = AppColors.RankingContent,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(12.dp))
            WeekSelector(
                week = uiState.weekLabel.ifBlank { "주간 랭킹" },
                onPrevious = onPreviousWeek,
                onNext = onNextWeek,
                modifier = Modifier.padding(horizontal = 24.dp),
                canGoPrevious = uiState.status == WeeklyRankingStatus.Ready && uiState.hasPrevious,
                canGoNext = uiState.status == WeeklyRankingStatus.Ready && uiState.weeksAgo > 0,
            )
            Spacer(Modifier.height(22.dp))
            when (uiState.status) {
                WeeklyRankingStatus.Loading -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator(color = AppColors.RankingContent)
                }

                WeeklyRankingStatus.Failed -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("랭킹을 불러오지 못했어요.", color = AppColors.RankingContent, fontSize = 14.sp)
                        Button(onClick = onRetry) { Text("다시 시도") }
                    }
                }

                WeeklyRankingStatus.Ready -> {
                    if (uiState.rankings.isEmpty()) {
                        Text(
                            "해당 주에 랭킹이 없어요.",
                            modifier = Modifier.padding(vertical = 48.dp),
                            color = AppColors.RankingSecondaryContent,
                            fontSize = 14.sp,
                        )
                    } else if (uiState.rankings.size >= 3) {
                        TopThreeRanking(members = uiState.rankings.take(3))
                        Spacer(Modifier.height(22.dp))
                        uiState.rankings.drop(3).forEach { member ->
                            RankingRow(member = member, modifier = Modifier.padding(bottom = 14.dp))
                        }
                    } else {
                        uiState.rankings.forEach { member ->
                            RankingRow(member = member, modifier = Modifier.padding(bottom = 14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Preview
@Composable
private fun WeeklyRankingScreenPreview() {
    WeeklyRankingScreen(
        uiState =
            WeeklyRankingUiState(
                weeksAgo = 0,
                weekLabel = sampleWeeks[2],
                hasPrevious = true,
                rankings = sampleRankings,
                status = WeeklyRankingStatus.Ready,
            ),
        onPreviousWeek = {},
        onNextWeek = {},
        onRetry = {},
    )
}
