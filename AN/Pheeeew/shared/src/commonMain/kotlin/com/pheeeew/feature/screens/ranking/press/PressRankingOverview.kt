package com.pheeeew.feature.screens.ranking.press

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.RefreshErrorBanner
import com.pheeeew.feature.screens.ranking.press.components.MyRaceRankCard
import com.pheeeew.feature.screens.ranking.press.components.PressRaceHeader
import com.pheeeew.feature.screens.ranking.press.components.RankingEncouragementBanner
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_daily_ranking_people
import pheeeew.shared.generated.resources.press_daily_ranking_refresh_error

@Composable
internal fun PressRankingOverview(
    state: PressRankingUiState,
    dailyDate: String?,
    encouragement: StringResource,
    rankWidth: Dp,
    countWidth: Dp,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    compact: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        RankingEncouragementBanner(message = encouragement, compact = compact)
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        MyRaceRankCard(state.me, state.status, onBack, onRetry, compact = compact)
        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.press_daily_ranking_people),
                Modifier.weight(1f).padding(end = 12.dp),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state.date != null && dailyDate == null) {
                Text(
                    state.date,
                    Modifier.weight(0.65f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF767D78),
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        PressRaceHeader(rankWidth, countWidth)
        if (state.hasRefreshError) {
            RefreshErrorBanner(stringResource(Res.string.press_daily_ranking_refresh_error), onRefresh)
        }
    }
}
