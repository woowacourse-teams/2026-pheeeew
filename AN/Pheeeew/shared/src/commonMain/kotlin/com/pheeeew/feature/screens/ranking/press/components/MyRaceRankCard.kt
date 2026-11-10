package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.raceCountText
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
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

@Composable
internal fun MyRaceRankCard(
    me: PressPersonRank?,
    status: PressRankingStatus,
    onPress: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    historical: Boolean = false,
    reducedMotion: Boolean = false,
) {
    if (status == PressRankingStatus.Ready && me == null) {
        MyRaceEmptyCard(historical, onPress, modifier)
        return
    }
    if (compact && status == PressRankingStatus.Ready && me != null) {
        Column(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.appColors.press.myRaceCardAccent, AppShapes.RankingRow)
                .border(AppBorders.Thin, MaterialTheme.colorScheme.appInk, AppShapes.RankingRow)
                .padding(horizontal = AppSpacing.lg.dp, vertical = AppSpacing.sm.copy(value = 10.dp).dp),
        ) {
            Text(
                if (historical) "이날 나의 레이스" else stringResource(Res.string.press_my_rank_title),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.appInk,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.press_my_rank_position, me.rank.toString()),
                    Modifier.weight(1f),
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.appInk,
                )
                Text(
                    stringResource(Res.string.press_my_rank_count, raceCountText(me.count)),
                    Modifier.weight(1f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.appInk,
                    textAlign = TextAlign.End,
                )
            }
        }
        return
    }
    if (status != PressRankingStatus.Ready || me == null) {
        val title =
            when (status) {
                PressRankingStatus.Loading -> {
                    stringResource(Res.string.press_my_rank_loading)
                }

                PressRankingStatus.Failed -> {
                    stringResource(Res.string.press_my_rank_failed)
                }

                PressRankingStatus.Ready -> {
                    if (historical) {
                        "이날 참여 기록이 없어요."
                    } else {
                        stringResource(
                            Res.string.press_my_rank_empty,
                        )
                    }
                }
            }
        Column(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.appColors.press.myRaceRowSurface, AppShapes.RankingRow)
                .border(AppBorders.Thin, MaterialTheme.appColors.press.rankingCardBorder, AppShapes.RankingRow)
                .padding(AppSpacing.lg.dp),
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.appInk)
            if (status == PressRankingStatus.Ready && !historical) {
                Text(
                    stringResource(Res.string.press_my_rank_invite),
                    fontSize = 12.sp,
                    color = MaterialTheme.appColors.press.rankingMuted,
                )
            }
            if (status == PressRankingStatus.Failed || (status == PressRankingStatus.Ready && !historical)) {
                Button(
                    onClick = if (status == PressRankingStatus.Ready) onPress else onRetry,
                    shape = AppShapes.CompactControl,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.appColors.press.myRaceCardAccent,
                            contentColor = MaterialTheme.colorScheme.appInk,
                        ),
                    modifier = Modifier.align(Alignment.End).padding(top = AppSpacing.sm.dp),
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
    val description =
        if (historical) {
            "이날 내 순위 ${me.rank}위, ${me.count}회 프레스"
        } else {
            stringResource(
                Res.string.press_my_rank_description,
                me.rank.toString(),
                me.count.toString(),
            )
        }
    val rankLabel = stringResource(Res.string.press_my_rank_position, me.rank.toString())
    val countLabel = stringResource(Res.string.press_my_rank_count, raceCountText(me.count))
    val shape = AppShapes.LargeCard
    val dateAccent = MaterialTheme.appColors.press.dateAccent
    val raceAccent = MaterialTheme.appColors.press.myRaceAccent
    Box(modifier.fillMaxWidth().padding(bottom = AppSpacing.xs.dp)) {
        Box(Modifier.matchParentSize().offset(y = 4.dp).background(MaterialTheme.colorScheme.appInk, shape))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.appColors.press.myRaceCardAccent)
                .border(AppBorders.Standard, MaterialTheme.colorScheme.appInk, shape)
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
                drawPath(diagonal, dateAccent)
                for (fraction in listOf(0.62f, 0.74f)) {
                    drawLine(
                        raceAccent,
                        Offset(size.width * fraction, size.height),
                        Offset(size.width * (fraction + 0.38f), 0f),
                        2.dp.toPx(),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 144.dp,
                    ).padding(horizontal = AppSpacing.xl.dp, vertical = AppSpacing.lg.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (historical) "이날 나의 레이스" else stringResource(Res.string.press_my_rank_title),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.appInk,
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
                            color = MaterialTheme.colorScheme.appInk,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        countLabel,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.appColors.press.myRaceHighlightSurface,
                                    AppShapes.CompactControl,
                                ).border(
                                    AppBorders.Thin,
                                    MaterialTheme.colorScheme.appInk,
                                    AppShapes.CompactControl,
                                ).padding(
                                    horizontal = AppSpacing.sm.copy(value = 10.dp).dp,
                                    vertical = AppSpacing.xs.copy(value = 3.dp).dp,
                                ),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.appInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(AppSpacing.md.dp))
                Column(Modifier.weight(0.55f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        RunningRaceCharacter(
                            me.emotion,
                            Modifier.requiredSize(110.dp),
                            reducedMotion = reducedMotion,
                            phaseSeed = me.publicId?.hashCode() ?: 0,
                        )
                        if (me.rank == 1L) {
                            Text(
                                "✦",
                                fontSize = 27.sp,
                                color = MaterialTheme.colorScheme.appInk,
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = AppSpacing.sm.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.sm.copy(value = 10.dp).dp))
                    Text(
                        if (historical) {
                            "내 페이스로 달렸어요!"
                        } else {
                            when (me.rank) {
                                1L -> stringResource(Res.string.press_my_rank_first)
                                2L, 3L -> stringResource(Res.string.press_my_rank_top_three)
                                else -> stringResource(Res.string.press_my_rank_running)
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.appInk, AppShapes.Action)
                                .padding(horizontal = AppSpacing.xs.copy(value = 6.dp).dp, vertical = AppSpacing.xs.dp),
                        color = MaterialTheme.appColors.press.rankBadgeSurface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
