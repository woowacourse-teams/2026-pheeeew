package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_ranking_headline
import pheeeew.shared.generated.resources.press_ranking_subtitle

@Composable
internal fun PressRankingHeader(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(Res.string.press_ranking_headline),
            color = AppColors.RankingContent,
            fontSize = 22.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.press_ranking_subtitle),
            color = AppColors.RankingSecondaryContent,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Preview(name = "프레스 랭킹 헤더")
@Composable
private fun PressRankingHeaderPreview() {
    AppTheme {
        PressRankingHeader()
    }
}
