package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.ranking.press.PressGroupRank
import com.pheeeew.feature.screens.ranking.press.samplePressGroupRanks

@Composable
internal fun PressGroupRankRow(
    group: PressGroupRank,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    PressRankingCardSurface(
        modifier = modifier,
        color =
            if (group.isMyGroup) {
                androidx.compose.ui.graphics.Color(
                    0xFFFFF3B2,
                )
            } else {
                androidx.compose.ui.graphics.Color.White
            },
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.rank.toString().padStart(2, '0'),
                color = AppColors.RankingContent,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(18.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Reserve the same two-line text space at the current font scale.
                Column(
                    modifier =
                        if (group.isMyGroup) {
                            Modifier
                        } else {
                            Modifier.alpha(0f).clearAndSetSemantics {}
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = group.groupName,
                        color = AppColors.RankingContent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "내 그룹",
                        color = AppColors.RankingSecondaryContent,
                        fontSize = 12.sp,
                    )
                }
                if (!group.isMyGroup) {
                    Text(
                        text = group.groupName,
                        color = AppColors.RankingContent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = group.count.toString(),
                color = AppColors.RankingContent,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(name = "프레스 그룹 랭킹 행")
@Composable
private fun PressGroupRankRowPreview() {
    AppTheme {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PressGroupRankRow(samplePressGroupRanks[1])
            PressGroupRankRow(samplePressGroupRanks[0])
        }
    }
}

@Preview(name = "프레스 그룹 랭킹 행 - 큰 글씨", fontScale = 1.5f)
@Composable
private fun PressGroupRankRowLargeFontPreview() {
    PressGroupRankRowPreview()
}
