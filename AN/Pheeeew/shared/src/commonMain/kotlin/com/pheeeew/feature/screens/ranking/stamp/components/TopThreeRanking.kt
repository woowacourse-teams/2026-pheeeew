package com.pheeeew.feature.screens.ranking.stamp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.screens.ranking.stamp.RankingMember
import com.pheeeew.feature.screens.ranking.stamp.sampleRankings

@Composable
fun TopThreeRanking(
    members: List<RankingMember>,
    onGroupClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        RankingMemberCard(members[1], onClick = { onGroupClick(members[1].groupId) }, modifier = Modifier.weight(1f))
        RankingMemberCard(members[0], onClick = { onGroupClick(members[0].groupId) }, modifier = Modifier.weight(1f))
        RankingMemberCard(members[2], onClick = { onGroupClick(members[2].groupId) }, modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
private fun TopThreeRankingPreview() {
    TopThreeRanking(sampleRankings.take(3), onGroupClick = {})
}
