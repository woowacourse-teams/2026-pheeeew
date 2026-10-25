package com.pheeeew.feature.screens.press.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.feature.emotion.component.formatCount
import com.pheeeew.feature.screens.press.PressNearbyRankRow
import com.pheeeew.feature.screens.press.PressNearbyRankingStatus
import com.pheeeew.feature.screens.press.PressNearbyRankingUiState
import com.pheeeew.feature.screens.press.normalizedNearbyProgress
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_nearby_count
import pheeeew.shared.generated.resources.press_nearby_empty
import pheeeew.shared.generated.resources.press_nearby_loading
import pheeeew.shared.generated.resources.press_nearby_me
import pheeeew.shared.generated.resources.press_nearby_participate
import pheeeew.shared.generated.resources.press_nearby_progress
import pheeeew.shared.generated.resources.press_nearby_progress_unknown
import pheeeew.shared.generated.resources.press_nearby_reconnecting
import pheeeew.shared.generated.resources.press_nearby_row_description

private val RankingInk = Color(0xFF202323)
private val RankingMuted = Color(0xFF858B85)

@Composable
internal fun PressNearbyRankingCard(
    state: PressNearbyRankingUiState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E3DB), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        state.rows.forEach { NearbyRankRow(it) }
        if (state.participationPending) {
            Text(
                stringResource(Res.string.press_nearby_participate),
                color = RankingInk,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
            )
        }
        val message =
            when {
                state.status == PressNearbyRankingStatus.Loading -> {
                    stringResource(Res.string.press_nearby_loading)
                }

                state.status == PressNearbyRankingStatus.Reconnecting -> {
                    stringResource(
                        Res.string.press_nearby_reconnecting,
                    )
                }

                state.rows.isEmpty() && !state.participationPending -> {
                    stringResource(Res.string.press_nearby_empty)
                }

                else -> {
                    null
                }
            }
        if (message != null) Text(message, color = RankingMuted, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
        Spacer(Modifier.height(8.dp))
        val progress = normalizedNearbyProgress(state.progress)
        val label =
            stringResource(
                if (progress ==
                    null
                ) {
                    Res.string.press_nearby_progress_unknown
                } else {
                    Res.string.press_nearby_progress
                },
            )
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF0EB))
                .clearAndSetSemantics {
                    contentDescription = label
                    if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
        ) {
            if (progress != null &&
                progress > 0f
            ) {
                Box(Modifier.fillMaxWidth(progress).height(5.dp).background(RankingInk, CircleShape))
            }
        }
    }
}

@Composable
private fun NearbyRankRow(row: PressNearbyRankRow) {
    val shape = RoundedCornerShape(12.dp)
    val name = if (row.isMe) stringResource(Res.string.press_nearby_me) else row.nickname
    val count = stringResource(Res.string.press_nearby_count, formatCount(row.count))
    val description =
        stringResource(Res.string.press_nearby_row_description, row.rank.toString(), name, formatCount(row.count))
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 38.dp)
            .clip(shape)
            .background(if (row.isMe) Color(0xFFFFE466) else Color.Transparent)
            .then(if (row.isMe) Modifier.border(1.5.dp, RankingInk, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val medal =
            when (row.rank) {
                1L -> Color(0xFFFFD752)
                2L -> Color(0xFFCCD4DC)
                3L -> Color(0xFFDCA46C)
                else -> null
            }
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (medal !=
                null
            ) {
                Box(Modifier.size(24.dp).background(medal, CircleShape).border(1.dp, RankingInk, CircleShape))
            }
            Text(
                row.rank.toString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (row.isMe || medal != null) RankingInk else RankingMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (row.isMe) RankingInk else RankingMuted,
            fontSize = 13.sp,
            fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Medium,
        )
        Box(Modifier.widthIn(max = 120.dp)) {
            Text(
                count,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                color = RankingInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
