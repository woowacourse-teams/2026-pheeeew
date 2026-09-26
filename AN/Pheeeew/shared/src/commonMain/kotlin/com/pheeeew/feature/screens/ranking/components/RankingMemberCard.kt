package com.pheeeew.feature.screens.ranking.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.GroupStamp
import com.pheeeew.feature.screens.ranking.RankingMember
import com.pheeeew.feature.screens.ranking.sampleRankings
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.weekly_ranking_crown

@Composable
fun RankingMemberCard(
    member: RankingMember,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (member.rank == 1) 22.dp else 18.dp)
    Column(
        modifier =
            modifier
                .height(height)
                .border(1.dp, AppColors.RankingContent, shape)
                .background(AppColors.Background, shape)
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
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${member.rank}위", color = AppColors.RankingContent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (member.rank == 1) {
            Image(
                painter = painterResource(Res.drawable.weekly_ranking_crown),
                contentDescription = null,
                modifier = Modifier.size(width = 48.dp, height = 24.dp),
            )
        }
        GroupStamp("히유", if (member.rank == 1) 62.dp else 56.dp)
        Text(
            member.name,
            color = AppColors.RankingContent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 17.sp,
        )
        Text(
            "톡 개수 ${member.count}개",
            color = AppColors.RankingContent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun RankingMemberCardPreview() {
    RankingMemberCard(sampleRankings.first(), 196.dp, Modifier.padding(16.dp))
}
