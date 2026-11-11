package com.pheeeew.feature.screens.pressevent

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.feature.emotion.component.formatCount
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.components.RunningRaceCharacter

@Composable
internal fun EventLeaderCard(
    leader: PressPersonRank?,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val rankLabel = leader?.let { "${formatCount(it.count)}회" } ?: "—"
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
                .clearAndSetSemantics { contentDescription = "현재 1등, $rankLabel, ${leader?.nickname ?: "아직 기록이 없어요"}" },
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
                        "현재 1등",
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
                }
                Spacer(Modifier.width(AppSpacing.md.dp))
                Column(Modifier.weight(0.55f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        RunningRaceCharacter(
                            leader?.emotion ?: EmotionKind.Blocked,
                            Modifier.requiredSize(110.dp),
                            reducedMotion = reducedMotion,
                            phaseSeed = leader?.publicId?.hashCode() ?: 0,
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.sm.copy(value = 10.dp).dp))
                    Text(
                        leader?.nickname ?: "첫 도전을 기다리고 있어요",
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

@Preview(name = "이벤트 현재 1등", widthDp = 360)
@Composable
private fun EventLeaderCardPreview() {
    AppTheme {
        EventLeaderCard(
            PressPersonRank(
                rank = 1L,
                nickname = "한숨많은곰",
                count = 123L,
            ),
            Modifier.padding(16.dp),
            true,
        )
    }
}
