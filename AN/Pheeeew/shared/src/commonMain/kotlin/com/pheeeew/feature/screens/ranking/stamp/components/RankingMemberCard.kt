package com.pheeeew.feature.screens.ranking.stamp.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.ranking.stamp.RankingMember
import com.pheeeew.feature.screens.ranking.stamp.sampleRankings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_rank_number
import pheeeew.shared.generated.resources.ranking_score_count
import pheeeew.shared.generated.resources.ranking_stamp_label
import pheeeew.shared.generated.resources.weekly_ranking_crown

@Composable
fun RankingMemberCard(
    member: RankingMember,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .rankingCardBorder()
                .padding(
                    start = 8.dp,
                    top = if (member.rank == 1) 10.dp else 8.dp,
                    end = 8.dp,
                    bottom =
                        when (member.rank) {
                            1 -> 10.dp
                            2 -> 8.dp
                            else -> 4.dp
                        },
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.group_detail_rank_number, member.rank), color = AppColors.RankingContent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (member.rank == 1) {
            Image(
                painter = painterResource(Res.drawable.weekly_ranking_crown),
                contentDescription = null,
                modifier = Modifier.size(width = 48.dp, height = 24.dp),
            )
        }
        GroupStamp(member.stamp, if (member.rank == 1) 62.dp else 56.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                member.name,
                color = AppColors.RankingContent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 17.sp,
            )
            Text(stringResource(Res.string.ranking_stamp_label), color = AppColors.RankingContent, fontSize = 10.sp, lineHeight = 12.sp)
            Text(
                stringResource(Res.string.ranking_score_count, member.score),
                color = AppColors.RankingContent,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun RankingMemberCardPreview() {
    AppTheme {
        RankingMemberCard(sampleRankings.first(), Modifier.padding(16.dp))
    }
}
