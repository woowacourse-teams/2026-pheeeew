package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.feature.component.PressRankBadge
import com.pheeeew.feature.component.rememberCountUp
import com.pheeeew.feature.screens.ranking.press.PressRaceRowModel
import com.pheeeew.feature.screens.ranking.press.raceCountText
import com.pheeeew.feature.screens.ranking.press.raceTrackPosition
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_race_column_count
import pheeeew.shared.generated.resources.press_race_column_person
import pheeeew.shared.generated.resources.press_race_column_rank
import pheeeew.shared.generated.resources.press_race_column_track
import pheeeew.shared.generated.resources.press_race_me
import pheeeew.shared.generated.resources.press_race_my_row_description
import pheeeew.shared.generated.resources.press_race_row_description

private val ink = AppColors.GroupInk
private val muted = AppColors.Press.RankingMuted

@Composable
internal fun PressRaceHeader(
    rankWidth: Dp,
    countWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(
                min = 28.dp,
            ).padding(horizontal = AppSpacing.sm.dp, vertical = AppSpacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.press_race_column_rank),
            Modifier.width(rankWidth),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = muted,
        )
        Text(
            stringResource(Res.string.press_race_column_person),
            Modifier.weight(0.85f).padding(start = AppSpacing.sm.dp),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = muted,
        )
        Box(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.press_race_column_track),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = muted,
            )
        }
        Text(
            stringResource(Res.string.press_race_column_count),
            Modifier.width(countWidth),
            textAlign = TextAlign.End,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = muted,
        )
    }
}

@Composable
internal fun PressRaceRow(
    row: PressRaceRowModel,
    rankWidth: Dp,
    countWidth: Dp,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val description =
        stringResource(
            if (row.isMe) Res.string.press_race_my_row_description else Res.string.press_race_row_description,
            row.rank.toString(),
            row.nickname,
            row.count.toString(),
        )
    val displayedCount by rememberCountUp(row.count, reducedMotion)
    val shape = AppShapes.Action
    val background =
        when {
            row.isMe -> AppColors.Press.RaceHighlight
            row.rank in 1L..3L -> AppColors.Press.RaceTopThreeSurface
            else -> AppColors.Transparent
        }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 50.dp)
            .background(background, shape)
            .then(if (row.isMe) Modifier.border(AppBorders.Standard, ink, shape) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
            }.padding(horizontal = AppSpacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(rankWidth).padding(vertical = AppSpacing.md.dp), contentAlignment = Alignment.Center) {
            PressRankBadge(row.rank, numberColor = muted, numberSize = 13.sp)
        }

        Text(
            if (row.isMe) stringResource(Res.string.press_race_me) else row.nickname,
            Modifier.weight(0.85f).padding(start = AppSpacing.sm.dp, end = AppSpacing.xs.copy(value = 6.dp).dp),
            fontSize = 14.sp,
            color = ink,
            fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                val y = size.height / 2
                drawLine(
                    AppColors.Press.RaceProgress,
                    Offset(0f, y),
                    Offset(size.width, y),
                    7.dp.toPx(),
                    StrokeCap.Round,
                )
                val step = 15.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        AppColors.White,
                        Offset(x, y),
                        Offset((x + 5.dp.toPx()).coerceAtMost(size.width), y),
                        1.dp.toPx(),
                    )
                    x += step
                }
            }
            Box(
                modifier =
                    Modifier.fillMaxWidth().layout { measurable, constraints ->
                        val faceSize = minOf(30.dp.roundToPx(), constraints.maxWidth)
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minWidth = faceSize,
                                    maxWidth = faceSize,
                                    minHeight = faceSize,
                                    maxHeight = faceSize,
                                ),
                            )
                        val available = constraints.maxWidth
                        layout(available, placeable.height) {
                            val x =
                                (
                                    (available - placeable.width).coerceAtLeast(
                                        0,
                                    ) * raceTrackPosition(row.progress)
                                ).toInt()
                            placeable.placeRelative(x, 0)
                        }
                    },
            ) {
                RunningRaceCharacter(
                    emotion = row.emotion,
                    modifier = Modifier.fillMaxSize(),
                    reducedMotion = reducedMotion,
                    phaseSeed = row.publicId?.hashCode() ?: row.markerIndex,
                )
            }
        }
        Text(
            raceCountText(displayedCount),
            Modifier.width(countWidth).padding(start = AppSpacing.xs.dp),
            color = ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RaceFlag(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val x = size.width * 0.2f
        drawLine(ink, Offset(x, size.height), Offset(x, 0f), 1.5.dp.toPx())
        val flag =
            Path().apply {
                moveTo(x, 1f)
                lineTo(size.width * 0.85f, 1f)
                lineTo(size.width * 0.65f, size.height * 0.25f)
                lineTo(size.width * 0.85f, size.height * 0.48f)
                lineTo(x, size.height * 0.48f)
                close()
            }
        drawPath(flag, AppColors.Press.RaceHighlight)
        drawPath(flag, ink, style = Stroke(1.dp.toPx()))
    }
}
