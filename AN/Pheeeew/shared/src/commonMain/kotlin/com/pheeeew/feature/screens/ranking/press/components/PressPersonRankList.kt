package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.samplePressPersonRanks

@Composable
internal fun PressPersonRankList(
    people: List<PressPersonRank>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        people.forEach { person ->
            PressPersonRankRow(person)
        }
    }
}

@Preview(name = "개인 프레스 랭킹 목록")
@Composable
private fun PressPersonRankListPreview() {
    AppTheme {
        PressPersonRankList(samplePressPersonRanks)
    }
}
