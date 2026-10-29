package com.pheeeew.feature.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_ranking_medal_1
import pheeeew.shared.generated.resources.press_ranking_medal_2
import pheeeew.shared.generated.resources.press_ranking_medal_3

/** Both ranking surfaces use the server's rank, including ties, rather than row position. */
@Composable
internal fun PressRankBadge(
    rank: Long,
    modifier: Modifier = Modifier,
    numberColor: Color = Color(0xFF767D78),
    numberSize: TextUnit = 12.sp,
) {
    val medal =
        when (rank) {
            1L -> Res.drawable.press_ranking_medal_1
            2L -> Res.drawable.press_ranking_medal_2
            3L -> Res.drawable.press_ranking_medal_3
            else -> null
        }
    if (medal != null) {
        Image(painterResource(medal), contentDescription = null, modifier = modifier.size(26.dp))
    } else {
        Text(
            rank.toString(),
            modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = numberSize,
            fontWeight = FontWeight.Bold,
            color = numberColor,
        )
    }
}
