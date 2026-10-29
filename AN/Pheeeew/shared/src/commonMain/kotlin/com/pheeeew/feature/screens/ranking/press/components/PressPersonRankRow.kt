package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.samplePressPersonRanks

@Composable
internal fun PressPersonRankRow(
    person: PressPersonRank,
    modifier: Modifier = Modifier,
) {
    PressRankingCardSurface(
        modifier = modifier,
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = person.rank.toString().padStart(2, '0'),
                color = AppColors.RankingContent,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = person.nickname,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = AppColors.RankingContent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = person.count.toString(),
                color = AppColors.RankingContent,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(name = "개인 프레스 랭킹 행")
@Composable
private fun PressPersonRankRowPreview() {
    AppTheme {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PressPersonRankRow(samplePressPersonRanks[1])
            PressPersonRankRow(samplePressPersonRanks[0])
        }
    }
}

@Preview(name = "개인 프레스 랭킹 행 - 큰 글씨", fontScale = 1.5f)
@Composable
private fun PressPersonRankRowLargeFontPreview() {
    PressPersonRankRowPreview()
}
