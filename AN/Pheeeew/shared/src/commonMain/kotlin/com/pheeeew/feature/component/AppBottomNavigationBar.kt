package com.pheeeew.feature.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import kotlinx.coroutines.launch

private val NavigationInk = Color(0xFF202323)
internal val AppBottomNavigationBarHeight = 55.dp
internal val AppBottomNavigationBarBottomSpacing = 12.dp
internal val AppBottomNavigationBarContentGap = 12.dp
internal val AppBottomNavigationBarOverlaySpace =
    AppBottomNavigationBarHeight + AppBottomNavigationBarBottomSpacing + AppBottomNavigationBarContentGap

internal enum class AppDestination(
    val label: String,
) {
    Map("지도"),
    Group("그룹"),
    Ranking("랭킹"),
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
) {
    val navigationFont = notoSansKrFontFamily()
    val destinations = AppDestination.entries
    val selectedIndex = destinations.indexOf(selectedDestination)
    val coroutineScope = rememberCoroutineScope()
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
                .background(Color.White, CircleShape)
                .border(1.dp, NavigationInk, CircleShape)
                .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val itemWidth = maxWidth / destinations.size
        val selectedIndicatorOffset by
            animateDpAsState(
                targetValue = itemWidth * selectedIndex + 2.dp,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                label = "bottomNavigationIndicatorOffset",
            )
        Box(
            modifier =
                Modifier
                    .offset(x = selectedIndicatorOffset)
                    .width(itemWidth - 4.dp)
                    .height(43.dp)
                    .clip(CircleShape)
                    .background(AppColors.Primary),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(43.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val isSelected = destination == selectedDestination
                val iconScale = remember(destination) { Animatable(1f) }
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(43.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    coroutineScope.launch {
                                        iconScale.snapTo(0.88f)
                                        iconScale.animateTo(
                                            targetValue = 1.12f,
                                            animationSpec = spring(dampingRatio = 0.48f, stiffness = 560f),
                                        )
                                        iconScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 560f),
                                        )
                                    }
                                    onDestinationSelected(destination)
                                },
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
                        pressScale = iconScale.value,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = destination.label,
                        color = NavigationInk,
                        fontSize = 14.sp,
                        fontFamily = navigationFont,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
