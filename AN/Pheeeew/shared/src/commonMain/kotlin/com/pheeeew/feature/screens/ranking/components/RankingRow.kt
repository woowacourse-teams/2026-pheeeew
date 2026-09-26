package com.pheeeew.feature.screens.ranking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.GroupStamp
import com.pheeeew.feature.screens.ranking.RankingMember
import com.pheeeew.feature.screens.ranking.sampleRankings

@Composable
fun RankingRow(
    member: RankingMember,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .border(1.dp, AppColors.RankingContent, shape)
                .background(AppColors.Background, shape)
                .height(73.dp)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "${member.rank}위",
            Modifier.weight(0.65f),
            color = AppColors.RankingContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        GroupStamp("하유", 47.dp)
        Text(
            member.name,
            Modifier.weight(2f),
            color = AppColors.RankingContent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text("톡 개수", color = AppColors.RankingSecondaryContent, fontSize = 11.sp)
            Text("${member.count}개", color = AppColors.RankingContent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview
@Composable
private fun RankingRowPreview() {
    RankingRow(sampleRankings[3], Modifier.padding(vertical = 16.dp))
}
