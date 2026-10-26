package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.raceCountText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_blocked_stage_0
import pheeeew.shared.generated.resources.press_my_rank_count
import pheeeew.shared.generated.resources.press_my_rank_description
import pheeeew.shared.generated.resources.press_my_rank_empty
import pheeeew.shared.generated.resources.press_my_rank_failed
import pheeeew.shared.generated.resources.press_my_rank_first
import pheeeew.shared.generated.resources.press_my_rank_go
import pheeeew.shared.generated.resources.press_my_rank_invite
import pheeeew.shared.generated.resources.press_my_rank_loading
import pheeeew.shared.generated.resources.press_my_rank_position
import pheeeew.shared.generated.resources.press_my_rank_retry
import pheeeew.shared.generated.resources.press_my_rank_running
import pheeeew.shared.generated.resources.press_my_rank_title
import pheeeew.shared.generated.resources.press_my_rank_top_three

private val cardInk = Color(0xFF202323)
private val cardYellow = Color(0xFFFFE66B)

@Composable
internal fun MyRaceRankCard(
    me: PressPersonRank?,
    status: PressRankingStatus,
    onPress: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status != PressRankingStatus.Ready || me == null) {
        val title =
            when (status) {
                PressRankingStatus.Loading -> stringResource(Res.string.press_my_rank_loading)
                PressRankingStatus.Failed -> stringResource(Res.string.press_my_rank_failed)
                PressRankingStatus.Ready -> stringResource(Res.string.press_my_rank_empty)
            }
        Column(
            modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F3ED), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFFDFE3D9), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = cardInk)
            if (status == PressRankingStatus.Ready) {
                Text(stringResource(Res.string.press_my_rank_invite), fontSize = 12.sp, color = Color(0xFF767D78))
            }
            if (status != PressRankingStatus.Loading) {
                Button(
                    onClick = if (status == PressRankingStatus.Ready) onPress else onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cardYellow, contentColor = cardInk),
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                ) {
                    Text(
                        if (status ==
                            PressRankingStatus.Ready
                        ) {
                            stringResource(Res.string.press_my_rank_go)
                        } else {
                            stringResource(Res.string.press_my_rank_retry)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        return
    }
    val description = stringResource(Res.string.press_my_rank_description, me.rank.toString(), me.count.toString())
    val rankLabel = stringResource(Res.string.press_my_rank_position, me.rank.toString())
    val countLabel = stringResource(Res.string.press_my_rank_count, raceCountText(me.count))
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(Modifier.matchParentSize().offset(y = 4.dp).background(cardInk, shape))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(cardYellow)
                .border(2.dp, cardInk, shape)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val diagonal =
                    Path().apply {
                        moveTo(size.width * 0.46f, size.height)
                        lineTo(size.width * 0.87f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                drawPath(diagonal, Color(0xFFFFF1A2))
                for (fraction in listOf(0.62f, 0.74f)) {
                    drawLine(
                        Color(0xFFF4D052),
                        Offset(size.width * fraction, size.height),
                        Offset(size.width * (fraction + 0.38f), 0f),
                        2.dp.toPx(),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 144.dp).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(Res.string.press_my_rank_title),
                        fontSize = 11.sp,
                        color = cardInk,
                        fontWeight = FontWeight.Bold,
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val measurer = rememberTextMeasurer()
                        val style = LocalTextStyle.current.copy(fontSize = 56.sp, fontWeight = FontWeight.Black)
                        val natural = measurer.measure(rankLabel, style, softWrap = false).size.width
                        val available = with(LocalDensity.current) { maxWidth.toPx() }
                        val ratio = (available / natural.coerceAtLeast(1)).coerceAtMost(1f)
                        Text(
                            rankLabel,
                            fontSize = (56f * ratio).sp,
                            fontWeight = FontWeight.Black,
                            color = cardInk,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        countLabel,
                        modifier =
                            Modifier
                                .background(Color(0xFFFFFAF0), RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    cardInk,
                                    RoundedCornerShape(12.dp),
                                ).padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(0.55f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Image(painterResource(Res.drawable.press_blocked_stage_0), null, Modifier.requiredSize(140.dp))
                        if (me.rank == 1) {
                            Text(
                                "✦",
                                fontSize = 27.sp,
                                color = cardInk,
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (me.rank) {
                            1 -> stringResource(Res.string.press_my_rank_first)
                            2, 3 -> stringResource(Res.string.press_my_rank_top_three)
                            else -> stringResource(Res.string.press_my_rank_running)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(cardInk, RoundedCornerShape(14.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        color = Color(0xFFFFF9DD),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
