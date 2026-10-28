package com.pheeeew.feature.screens.press.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.pheeeew.feature.component.PressRankBadge
import com.pheeeew.feature.emotion.component.formatCount
import com.pheeeew.feature.screens.press.PressActivityUiState
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
import pheeeew.shared.generated.resources.press_nearby_progress
import pheeeew.shared.generated.resources.press_nearby_reconnecting
import pheeeew.shared.generated.resources.press_nearby_row_description
import pheeeew.shared.generated.resources.press_nearby_unavailable

private val RankingInk = Color(0xFF202323)
private val RankingMuted = Color(0xFF858B85)

@Composable
internal fun PressNearbyRankingCard(
    state: PressNearbyRankingUiState,
    modifier: Modifier = Modifier,
    activity: PressActivityUiState? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E3DB), shape),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PressNearbyRankingRows(state)
            val message =
                when {
                    state.status == PressNearbyRankingStatus.Unavailable -> {
                        stringResource(Res.string.press_nearby_unavailable)
                    }

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
            if (message !=
                null
            ) {
                Text(message, color = RankingMuted, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
            val progress = normalizedNearbyProgress(state.progress)
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                val label = stringResource(Res.string.press_nearby_progress)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF0EB))
                        .clearAndSetSemantics {
                            contentDescription = label
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        },
                ) {
                    if (progress > 0f) {
                        Box(Modifier.fillMaxWidth(progress).height(5.dp).background(RankingInk, CircleShape))
                    }
                }
            }
        }
        if (activity != null) PressActivityFooter(activity)
    }
}

@Composable
internal fun NearbyRankRow(
    row: PressNearbyRankRow,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val name = if (row.isMe) stringResource(Res.string.press_nearby_me) else row.nickname
    val count = stringResource(Res.string.press_nearby_count, formatCount(row.count))
    val description =
        stringResource(Res.string.press_nearby_row_description, row.rank.toString(), name, formatCount(row.count))
    Row(
        modifier
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
        Box(Modifier.widthIn(min = 26.dp, max = 96.dp), contentAlignment = Alignment.Center) {
            Crossfade(row.rank, animationSpec = tween(if (reducedMotion) 0 else 120), label = "nearby rank") { rank ->
                PressRankBadge(rank, numberColor = if (row.isMe) RankingInk else RankingMuted)
            }
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
            Crossfade(count, animationSpec = tween(if (reducedMotion) 0 else 140), label = "nearby count") { label ->
                Text(
                    label,
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
}
