package com.pheeeew.feature.screens.ranking.stamp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.ranking.stamp.RankingMember
import com.pheeeew.feature.screens.ranking.stamp.sampleRankings
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_rank_number
import pheeeew.shared.generated.resources.ranking_score_count
import pheeeew.shared.generated.resources.ranking_stamp_label

@Composable
fun RankingRow(
    member: RankingMember,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .rankingCardBorder()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(Res.string.group_detail_rank_number, member.rank),
            Modifier.weight(0.65f),
            color = AppColors.RankingContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        GroupStamp(member.stamp, 47.dp)
        Text(
            member.name,
            Modifier.weight(2f),
            color = AppColors.RankingContent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(Res.string.ranking_stamp_label), color = AppColors.RankingSecondaryContent, fontSize = 11.sp)
            Text(stringResource(Res.string.ranking_score_count, member.score), color = AppColors.RankingContent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview
@Composable
private fun RankingRowPreview() {
    RankingRow(sampleRankings[0], Modifier.padding(vertical = 16.dp))
}
