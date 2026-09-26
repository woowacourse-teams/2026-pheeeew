package com.pheeeew.feature.screens.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.ranking.components.RankingRow
import com.pheeeew.feature.screens.ranking.components.TopThreeRanking
import com.pheeeew.feature.screens.ranking.components.WeekSelector

@Composable
fun WeeklyRankingScreen(modifier: Modifier = Modifier) {
    var selectedWeek by remember { mutableStateOf(2) }

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
                week = sampleWeeks[selectedWeek],
                onPrevious = { if (selectedWeek > 0) selectedWeek-- },
                onNext = { if (selectedWeek < sampleWeeks.lastIndex) selectedWeek++ },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(22.dp))
            TopThreeRanking(members = sampleRankings.take(3))
            Spacer(Modifier.height(22.dp))
            sampleRankings.drop(3).forEach { member ->
                RankingRow(member = member, modifier = Modifier.padding(bottom = 14.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Preview
@Composable
private fun WeeklyRankingScreenPreview() {
    WeeklyRankingScreen()
}
