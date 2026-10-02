package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.ranking.press.PressGroupRank
import com.pheeeew.feature.screens.ranking.press.samplePressGroupRanks

@Composable
internal fun PressGroupRankList(
    groups: List<PressGroupRank>,
    modifier: Modifier = Modifier,
    onGroupClick: (PressGroupRank) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            PressGroupRankRow(group, onClick = { onGroupClick(group) })
        }
    }
}

@Preview(name = "프레스 그룹 랭킹 목록")
@Composable
private fun PressGroupRankListPreview() {
    AppTheme {
        PressGroupRankList(samplePressGroupRanks)
    }
}
