package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.feature.screens.ranking.press.PressRaceRowModel

private val ink = Color(0xFF202323)
private val muted = Color(0xFF767D78)
private val markerColors = listOf(Color(0xFFF3B8A4), Color(0xFFE5B4C8), Color(0xFFB4CCD8))

@Composable
internal fun PressRaceHeader(
    rankWidth: Dp,
    countWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(28.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("순위", Modifier.width(rankWidth), fontSize = 11.sp, color = muted)
        Text("참여자", Modifier.weight(0.85f).padding(start = 8.dp), fontSize = 11.sp, color = muted)
        Box(Modifier.weight(1f)) {
            Text("레이스", fontSize = 11.sp, color = muted)
            RaceFlag(Modifier.align(Alignment.CenterEnd).offset(x = 12.8.dp).size(16.dp))
        }
        Text("횟수", Modifier.width(countWidth), textAlign = TextAlign.End, fontSize = 11.sp, color = muted)
    }
}

@Composable
internal fun PressRaceRow(
    row: PressRaceRowModel,
    rankWidth: Dp,
    countWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val background =
        when {
            row.isMe -> Color(0xFFFFE76A)
            row.rank in 1..3 -> Color(0xFFF4F4F0)
            else -> Color.Transparent
        }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 50.dp)
            .background(background, shape)
            .then(if (row.isMe) Modifier.border(2.dp, ink, shape) else Modifier)
            .clearAndSetSemantics {
                contentDescription = "${row.rank}위, ${row.nickname}${if (row.isMe) ", 나" else ""}, ${row.count}회"
            }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(rankWidth).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            if (row.rank in 1..3) {
                Canvas(Modifier.size(21.dp)) {
                    val color =
                        when (row.rank) {
                            1 -> Color(0xFFFFD851)
                            2 -> Color(0xFFC8CDCC)
                            else -> Color(0xFFDCA46C)
                        }
                    drawCircle(color)
                    drawCircle(ink, style = Stroke(1.dp.toPx()))
                }
            }
            Text(
                row.rank.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color =
                    if (row.rank in
                        1..3
                    ) {
                        ink
                    } else {
                        muted
                    },
            )
        }
        Text(
            if (row.isMe) "나" else row.nickname,
            Modifier.weight(0.85f).padding(start = 8.dp, end = 6.dp),
            fontSize = 14.sp,
            color = ink,
            fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val end = size.width
            val radius = minOf(5.5.dp.toPx(), end / 2)
            val progress = row.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            val x = radius + (end - radius * 2).coerceAtLeast(0f) * progress
            val y = size.height / 2
            drawLine(Color(0xFFE1E3DB), Offset(radius, y), Offset(end - radius, y), 3.5.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFFC99494), Offset(radius, y), Offset(x, y), 3.5.dp.toPx(), StrokeCap.Round)
            drawLine(
                Color(0xFF9B9F98),
                Offset(end, 0f),
                Offset(end, size.height),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            )
            val trailStart = (x - radius - 7.dp.toPx()).coerceAtLeast(0f)
            drawLine(
                muted,
                Offset(trailStart, y - 2.dp.toPx()),
                Offset(
                    (x - radius - 1.dp.toPx()).coerceAtLeast(0f),
                    y - 2.dp.toPx(),
                ),
                1.dp.toPx(),
            )
            drawLine(
                muted,
                Offset(trailStart, y + 2.dp.toPx()),
                Offset(
                    (x - radius - 3.dp.toPx()).coerceAtLeast(0f),
                    y + 2.dp.toPx(),
                ),
                1.dp.toPx(),
            )
            drawCircle(markerColors[row.markerIndex.mod(markerColors.size)], radius, Offset(x, y))
            drawCircle(ink, radius, Offset(x, y), style = Stroke(1.dp.toPx()))
        }
        Text(
            "${row.count}회",
            Modifier.width(countWidth).padding(start = 4.dp),
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
        drawPath(flag, Color(0xFFFFE76A))
        drawPath(flag, ink, style = Stroke(1.dp.toPx()))
    }
}
