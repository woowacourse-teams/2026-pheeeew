package com.pheeeew.feature.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.hiyu_nav_face
import pheeeew.shared.generated.resources.hiyu_nav_ticket
import pheeeew.shared.generated.resources.navigation_group
import pheeeew.shared.generated.resources.navigation_map
import pheeeew.shared.generated.resources.navigation_press
import pheeeew.shared.generated.resources.navigation_ranking
import pheeeew.shared.generated.resources.ranking_stamps

private val NavigationInk = Color(0xFF202323)
internal val AppBottomNavigationBarHeight = 55.dp
internal val AppBottomNavigationBarBottomSpacing = 12.dp
internal val AppBottomNavigationBarContentGap = 12.dp
internal val AppBottomNavigationBarOverlaySpace =
    AppBottomNavigationBarHeight + AppBottomNavigationBarBottomSpacing + AppBottomNavigationBarContentGap

internal enum class AppDestination {
    Map,
    Group,
    Ranking,
}

internal enum class RankingBottomNavigationDestination {
    Stamp,
    Press,
}

private enum class DestinationIcon(
    val visualScale: Float,
) {
    Map(0.96f),
    Group(1.02f),
    Ranking(1.2f),
}

@Composable
internal fun AppBottomNavigationBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    rankingDestination: RankingBottomNavigationDestination = RankingBottomNavigationDestination.Stamp,
    onRankingBackClick: () -> Unit = {},
    onRankingDestinationSelected: (RankingBottomNavigationDestination) -> Unit = {},
) {
    val navigationFont = notoSansKrFontFamily()
    val isRankingMode = selectedDestination == AppDestination.Ranking
    val mainDestinations = AppDestination.entries
    var previousMainDestination by
        remember {
            mutableStateOf(if (isRankingMode) AppDestination.Map else selectedDestination)
        }
    LaunchedEffect(selectedDestination) {
        if (selectedDestination != AppDestination.Ranking) {
            previousMainDestination = selectedDestination
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 356.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(AppBottomNavigationBarHeight)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .border(AppBorders.Standard, NavigationInk, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        val backSlotWidth = 48.dp
        val mainItemWidth = maxWidth / mainDestinations.size
        val rankingItemWidth = (maxWidth - backSlotWidth) / RankingBottomNavigationDestination.entries.size
        val indicatorInset = AppBorders.Standard + 1.5.dp
        val motionProgress by
            animateFloatAsState(
                targetValue = if (isRankingMode) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 480f),
                label = "bottomNavigationMotionProgress",
            )
        val tabProgress by
            animateFloatAsState(
                targetValue = if (rankingDestination == RankingBottomNavigationDestination.Press) 1f else 0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                label = "rankingTabProgress",
            )
        val rankingItemOffset =
            mainItemWidth * AppDestination.Ranking.ordinal * (1f - motionProgress) +
                backSlotWidth * motionProgress
        val animatedRankingItemWidth =
            mainItemWidth * (1f - motionProgress) + rankingItemWidth * motionProgress
        val selectedPillWidth = mainItemWidth - indicatorInset * 2
        val stampCenter = backSlotWidth + rankingItemWidth / 2
        val pressCenter = backSlotWidth + rankingItemWidth * 1.5f
        val secondaryItemsAlpha = 1f - (motionProgress / 0.34f).coerceIn(0f, 1f)
        val backButtonAlpha = ((motionProgress - 0.03f) / 0.5f).coerceIn(0f, 1f)
        val pressItemAlpha = ((motionProgress - 0.32f) / 0.63f).coerceIn(0f, 1f)
        val mainPillCenter by
            animateDpAsState(
                targetValue = mainItemWidth * (previousMainDestination.ordinal + 0.5f),
                animationSpec =
                    tween(
                        durationMillis = 240,
                        easing = CubicBezierEasing(0f, 0f, 0.33f, 1f),
                    ),
                label = "mainNavigationIndicatorOffset",
            )
        val contentMorph = ((motionProgress - 0.17f) / 0.37f).coerceIn(0f, 1f)
        val rankingPillCenter =
            mainPillCenter * (1f - motionProgress) +
                (stampCenter + (pressCenter - stampCenter) * tabProgress) * motionProgress

        Box(
            modifier =
                Modifier
                    .offset(x = rankingPillCenter - selectedPillWidth / 2)
                    .width(selectedPillWidth)
                    .height(AppBottomNavigationBarHeight - indicatorInset * 2)
                    .clip(CircleShape)
                    .background(AppColors.Primary)
                    .border(AppBorders.Standard, NavigationInk, CircleShape),
        )

        mainDestinations.filter { it != AppDestination.Ranking }.forEach { destination ->
            val index = destination.ordinal
            val isSelected = !isRankingMode && destination == selectedDestination
            Row(
                modifier =
                    Modifier
                        .offset(x = mainItemWidth * index)
                        .width(mainItemWidth)
                        .height(AppBottomNavigationBarHeight)
                        .graphicsLayer {
                            alpha = secondaryItemsAlpha
                            translationX = (-12f * motionProgress).dp.toPx()
                        }.clickable(
                            enabled = !isRankingMode,
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onDestinationSelected(destination) },
                        ).semantics { selected = isSelected }
                        .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                DestinationIconView(
                    when (destination) {
                        AppDestination.Map -> DestinationIcon.Map
                        AppDestination.Group -> DestinationIcon.Group
                        AppDestination.Ranking -> DestinationIcon.Ranking
                    },
                    pressScale = 1f,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        when (destination) {
                            AppDestination.Map -> Res.string.navigation_map
                            AppDestination.Group -> Res.string.navigation_group
                            AppDestination.Ranking -> Res.string.navigation_ranking
                        },
                    ),
                    color = NavigationInk,
                    fontSize = 14.sp,
                    fontFamily = navigationFont,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        val isRankingTabSelected =
            if (isRankingMode) {
                rankingDestination == RankingBottomNavigationDestination.Stamp
            } else {
                selectedDestination == AppDestination.Ranking
            }
        Row(
            modifier =
                Modifier
                    .offset(x = rankingItemOffset)
                    .width(animatedRankingItemWidth)
                    .height(AppBottomNavigationBarHeight)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            if (isRankingMode) {
                                onRankingDestinationSelected(RankingBottomNavigationDestination.Stamp)
                            } else {
                                onDestinationSelected(AppDestination.Ranking)
                            }
                        },
                    ).semantics { selected = isRankingTabSelected }
                    .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = 1f - contentMorph
                            translationY = (-4f * contentMorph).dp.toPx()
                            scaleX = 1f - 0.12f * contentMorph
                            scaleY = scaleX
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DestinationIconView(DestinationIcon.Ranking, pressScale = 1f)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.navigation_ranking),
                        color = NavigationInk,
                        fontSize = 14.sp,
                        fontFamily = navigationFont,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = contentMorph
                            translationY = (4f * (1f - contentMorph)).dp.toPx()
                            scaleX = 0.88f + 0.12f * contentMorph
                            scaleY = scaleX
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.hiyu_nav_ticket),
                        contentDescription = null,
                        modifier = Modifier.size(width = 22.dp, height = 17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.ranking_stamps),
                        color = NavigationInk,
                        fontSize = 14.sp,
                        fontFamily = navigationFont,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .offset(x = if (isRankingMode) 4.dp + 16.dp * (1f - motionProgress) else -48.dp)
                    .size(40.dp)
                    .graphicsLayer {
                        alpha = backButtonAlpha
                        scaleX = 0.7f + 0.3f * backButtonAlpha
                        scaleY = scaleX
                    }.clip(CircleShape)
                    .background(Color(0xFFF2F2F2))
                    .clickable(
                        enabled = isRankingMode,
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = onRankingBackClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            RankingNavigationIcon(isBack = true, modifier = Modifier.size(20.dp))
        }

        val isPressTabSelected = isRankingMode && rankingDestination == RankingBottomNavigationDestination.Press
        Row(
            modifier =
                Modifier
                    .offset(
                        x =
                            if (isRankingMode) {
                                backSlotWidth + rankingItemWidth + 18.dp * (1f - motionProgress)
                            } else {
                                maxWidth
                            },
                    ).width(rankingItemWidth)
                    .height(AppBottomNavigationBarHeight)
                    .graphicsLayer { alpha = pressItemAlpha }
                    .clickable(
                        enabled = isRankingMode,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onRankingDestinationSelected(RankingBottomNavigationDestination.Press) },
                    ).semantics { selected = isPressTabSelected }
                    .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.hiyu_nav_face),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.navigation_press),
                color = NavigationInk,
                fontSize = 14.sp,
                fontFamily = navigationFont,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RankingNavigationIcon(
    isBack: Boolean,
    modifier: Modifier = Modifier,
    destination: RankingBottomNavigationDestination? = null,
) {
    Canvas(modifier) {
        scale(size.width / 20f, size.height / 20f, pivot = Offset.Zero) {
            val strokeWidth = 1.6f
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (isBack) {
                drawLine(NavigationInk, Offset(12f, 3f), Offset(6f, 9f), strokeWidth, cap = StrokeCap.Round)
                drawLine(NavigationInk, Offset(6f, 9f), Offset(12f, 15f), strokeWidth, cap = StrokeCap.Round)
                drawLine(NavigationInk, Offset(6f, 9f), Offset(17f, 9f), strokeWidth, cap = StrokeCap.Round)
            } else if (destination == RankingBottomNavigationDestination.Stamp) {
                val ticket =
                    Path().apply {
                        moveTo(3f, 5f)
                        lineTo(17f, 5f)
                        lineTo(17f, 15f)
                        lineTo(3f, 15f)
                        close()
                    }
                drawPath(ticket, Color.White)
                drawPath(ticket, NavigationInk, style = stroke)
                drawLine(NavigationInk, Offset(6f, 8f), Offset(6f, 12f), 1f, cap = StrokeCap.Round)
            } else {
                val center = Offset(10f, 10f)
                drawCircle(Color.White, 8f, center)
                drawCircle(NavigationInk, 8f, center, style = stroke)
                val leftEye =
                    Path().apply {
                        moveTo(4f, 7f)
                        lineTo(8f, 9f)
                        lineTo(4f, 11f)
                    }
                val rightEye =
                    Path().apply {
                        moveTo(16f, 7f)
                        lineTo(12f, 9f)
                        lineTo(16f, 11f)
                    }
                val annoyedMouth =
                    Path().apply {
                        moveTo(6f, 14f)
                        lineTo(8f, 12f)
                        lineTo(10f, 14f)
                        lineTo(12f, 12f)
                        lineTo(14f, 14f)
                    }
                drawPath(leftEye, NavigationInk, style = stroke)
                drawPath(rightEye, NavigationInk, style = stroke)
                drawPath(annoyedMouth, NavigationInk, style = stroke)
            }
        }
    }
}

@Composable
private fun DestinationIconView(
    icon: DestinationIcon,
    pressScale: Float,
) {
    Canvas(
        Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = icon.visualScale * pressScale
                scaleY = icon.visualScale * pressScale
            },
    ) {
        scale(size.width / 20f, size.height / 20f, pivot = Offset.Zero) {
            when (icon) {
                DestinationIcon.Map -> {
                    val outline =
                        Path().apply {
                            moveTo(2.8f, 5.2f)
                            lineTo(7.6f, 2.8f)
                            lineTo(12.4f, 5.2f)
                            lineTo(17.2f, 2.8f)
                            lineTo(17.2f, 14.8f)
                            lineTo(12.4f, 17.2f)
                            lineTo(7.6f, 14.8f)
                            lineTo(2.8f, 17.2f)
                            close()
                        }
                    val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(outline, Color.White)
                    drawPath(outline, NavigationInk, style = stroke)
                    drawLine(NavigationInk, Offset(7.6f, 2.8f), Offset(7.6f, 14.8f), 1.6f, cap = StrokeCap.Round)
                    drawLine(NavigationInk, Offset(12.4f, 5.2f), Offset(12.4f, 17.2f), 1.6f, cap = StrokeCap.Round)
                }

                DestinationIcon.Group -> {
                    val stroke = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                    fun personBody(left: Float) =
                        Path().apply {
                            moveTo(left, 16.3953f)
                            lineTo(left, 14.6953f)
                            cubicTo(left, 11.8953f, left + 1.8f, 10.1953f, left + 4.6f, 10.1953f)
                            cubicTo(left + 7.4f, 10.1953f, left + 9.2f, 11.8953f, left + 9.2f, 14.6953f)
                            lineTo(left + 9.2f, 16.3953f)
                            close()
                        }

                    fun drawPersonHead(centerX: Float) {
                        val center = Offset(centerX, 5.7953f)
                        drawCircle(Color.White, 2.6f, center)
                        drawCircle(NavigationInk, 2.6f, center, style = stroke)
                    }

                    fun drawPersonBody(left: Float) {
                        val body = personBody(left)
                        drawPath(body, Color.White)
                        drawPath(body, NavigationInk, style = stroke)
                    }

                    translate(top = 0.5f) {
                        drawPersonHead(7.841f)
                        drawPersonBody(3.241f)
                        drawPersonHead(12.161f)
                        drawPersonBody(7.561f)
                    }
                }

                DestinationIcon.Ranking -> {
                    val bars =
                        Path().apply {
                            moveTo(4.7f, 15.7f)
                            lineTo(4.7f, 8.6f)
                            lineTo(8.23f, 8.6f)
                            lineTo(8.23f, 4.3f)
                            lineTo(11.77f, 4.3f)
                            lineTo(11.77f, 11.1f)
                            lineTo(15.3f, 11.1f)
                            lineTo(15.3f, 15.7f)
                            close()
                        }
                    val stroke = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(bars, Color.White)
                    drawPath(bars, NavigationInk, style = stroke)
                    drawLine(NavigationInk, Offset(8.23f, 8.6f), Offset(8.23f, 15.7f), 1.4f, cap = StrokeCap.Round)
                    drawLine(NavigationInk, Offset(11.77f, 11.1f), Offset(11.77f, 15.7f), 1.4f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppBottomNavigationBarPreview1() {
    AppBottomNavigationBar(
        selectedDestination = AppDestination.Ranking,
        onDestinationSelected = {},
    )
}

@Preview
@Composable
private fun AppBottomNavigationBarPreview2() {
    AppBottomNavigationBar(
        selectedDestination = AppDestination.Map,
        onDestinationSelected = {},
    )
}

@Preview
@Composable
private fun AppBottomNavigationBarGroupPreview() {
    AppBottomNavigationBar(
        selectedDestination = AppDestination.Group,
        onDestinationSelected = {},
    )
}
