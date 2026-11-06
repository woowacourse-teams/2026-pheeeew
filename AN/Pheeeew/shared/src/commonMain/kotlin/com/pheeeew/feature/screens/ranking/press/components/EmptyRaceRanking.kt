package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_blocked_stage_0

private val emptyInk = AppColors.GroupInk
private val emptyMuted = AppColors.Press.RankingMuted

@Composable
internal fun MyRaceEmptyCard(
    historical: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressRankingCardSurface(modifier, color = AppColors.Press.EmptyRankingHighlight) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(AppSpacing.lg.copy(value = 18.dp).dp)) {
            val stack = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.3f
            val copy: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm.copy(value = 10.dp).dp)) {
                    EmptyRaceLabel(if (historical) "이날 나의 레이스" else "오늘 나의 레이스")
                    Text(
                        if (historical) "이날은 남긴\n기록이 없어요" else "아직 남긴\n기록이 없어요",
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = emptyInk,
                    )
                    Text(
                        if (historical) "다른 날의 내 기록을 살펴보세요." else "첫 프레스로 레이스를 시작해 보세요.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = emptyMuted,
                    )
                    if (historical) {
                        EmptyRaceLabel("참여 기록 없음")
                    } else {
                        EmptyRaceAction("프레스 하러 가기", onPress)
                    }
                }
            }
            if (stack) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    copy()
                    EmptyRaceArtwork(Modifier.size(width = 180.dp, height = 120.dp), showCharacter = false)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { copy() }
                    Spacer(Modifier.width(AppSpacing.sm.dp))
                    EmptyRaceArtwork(Modifier.size(width = 110.dp, height = 135.dp), showCharacter = false)
                }
            }
        }
    }
}

@Composable
internal fun EmptyRaceRanking(
    historical: Boolean,
    onChooseDate: (() -> Unit)?,
    onToday: (() -> Unit)?,
    onPress: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Press.EmptyRankingSurface, AppShapes.SectionCard)
            .border(AppBorders.Thin, AppColors.Press.EmptyRankingBorder, AppShapes.SectionCard)
            .padding(
                horizontal = AppSpacing.xl.copy(value = 22.dp).dp,
                vertical = AppSpacing.xxl.copy(value = 28.dp).dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap.dp),
    ) {
        EmptyRaceLabel(if (historical) "이날의 레이스" else "오늘의 레이스")
        EmptyRaceArtwork(Modifier.fillMaxWidth().height(160.dp), halo = true)
        Text(
            if (historical) "이날은 참여 기록이 없어요" else "오늘의 첫 주인공이 되어볼까요?",
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = emptyInk,
        )
        Text(
            if (historical) {
                "이날 기록된 순위가 없어요.\n다른 날짜의 레이스를 살펴볼까요?"
            } else {
                "아직 시작된 레이스가 없어요.\n첫 프레스로 가볍게 시작해 보세요."
            },
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = emptyMuted,
        )
        if (historical) {
            onChooseDate?.let { EmptyRaceAction("다른 날짜 보기", it) }
            onToday?.let { EmptyRaceAction("오늘 랭킹으로 돌아가기", it, outlined = false) }
        } else {
            EmptyRaceAction("프레스 하러 가기", onPress)
            onChooseDate?.let { EmptyRaceAction("다른 날짜 보기", it, outlined = false) }
        }
    }
}

@Composable
private fun EmptyRaceLabel(text: String) {
    Text(
        text,
        Modifier
            .background(AppColors.Press.EmptyRankingSurface, RoundedCornerShape(50))
            .border(AppBorders.Thin, AppColors.Press.EmptyRankingPillBorder, RoundedCornerShape(50))
            .padding(horizontal = AppSpacing.sm.copy(value = 10.dp).dp, vertical = AppSpacing.xs.copy(value = 3.dp).dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = emptyInk,
    )
}

@Composable
private fun EmptyRaceAction(
    text: String,
    onClick: () -> Unit,
    outlined: Boolean = true,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (outlined) Modifier.border(AppBorders.Thin, emptyInk, AppShapes.Card) else Modifier)
            .clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = AppSpacing.md.dp, vertical = AppSpacing.sm.copy(value = 10.dp).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = emptyInk)
    }
}

@Composable
private fun EmptyRaceArtwork(
    modifier: Modifier = Modifier,
    halo: Boolean = false,
    showCharacter: Boolean = true,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (halo) Box(Modifier.size(135.dp).background(AppColors.Press.EmptyRankingHighlight, CircleShape))
        Canvas(Modifier.matchParentSize()) {
            val track =
                Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.78f)
                    cubicTo(
                        size.width * 0.5f,
                        size.height * 0.9f,
                        size.width * 0.65f,
                        size.height * 0.62f,
                        size.width * 0.86f,
                        size.height * 0.68f,
                    )
                }
            drawPath(
                track,
                emptyInk,
                style =
                    Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 7.dp.toPx())),
                    ),
            )
        }
        if (showCharacter) {
            Image(
                painterResource(Res.drawable.press_blocked_stage_0),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart).size(if (halo) 145.dp else 110.dp),
            )
        }
        Box(
            Modifier
                .align(
                    Alignment.CenterEnd,
                ).padding(end = AppSpacing.xs.copy(value = 6.dp).dp, top = AppSpacing.xxxl.copy(value = 35.dp).dp),
        ) {
            RaceFlag(Modifier.size(26.dp))
        }
    }
}
