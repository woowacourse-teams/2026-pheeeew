package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_ranking_hint_0
import pheeeew.shared.generated.resources.press_ranking_hint_1
import pheeeew.shared.generated.resources.press_ranking_hint_2
import pheeeew.shared.generated.resources.press_ranking_hint_3
import pheeeew.shared.generated.resources.press_ranking_hint_4
import kotlin.random.Random

private object RankingHints {
    private val messages =
        listOf(
            Res.string.press_ranking_hint_0,
            Res.string.press_ranking_hint_1,
            Res.string.press_ranking_hint_2,
            Res.string.press_ranking_hint_3,
            Res.string.press_ranking_hint_4,
        )
    private var nextIndex = Random.nextInt(messages.size)

    fun next() = messages[nextIndex].also { nextIndex = (nextIndex + 1) % messages.size }
}

@Composable
internal fun rememberRankingEncouragementMessage(): StringResource = remember { RankingHints.next() }

@Composable
internal fun RankingEncouragementBanner(
    modifier: Modifier = Modifier,
    message: StringResource = rememberRankingEncouragementMessage(),
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F1EB), shape)
            .border(1.dp, Color(0xFFDFE3D9), shape)
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RaceFlag(Modifier.size(20.dp))
        Text(
            stringResource(message),
            Modifier.weight(1f),
            fontSize = if (compact) 13.sp else 14.sp,
            lineHeight = if (compact) 18.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF202323),
        )
    }
}
