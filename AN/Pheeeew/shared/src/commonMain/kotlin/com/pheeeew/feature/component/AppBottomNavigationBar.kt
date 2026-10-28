package com.pheeeew.feature.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import pheeeew.shared.generated.resources.bottom_navigation_collection
import pheeeew.shared.generated.resources.bottom_navigation_map
import pheeeew.shared.generated.resources.bottom_navigation_press
import pheeeew.shared.generated.resources.nav_collection
import pheeeew.shared.generated.resources.nav_press

private val NavigationInk = Color(0xFF202323)
internal val AppBottomNavigationBarHeight = 57.dp
internal val AppBottomNavigationBarBottomSpacing = 12.dp
internal val AppBottomNavigationBarContentGap = 12.dp
internal val AppBottomNavigationBarOverlaySpace =
    AppBottomNavigationBarHeight + AppBottomNavigationBarBottomSpacing + AppBottomNavigationBarContentGap

internal enum class AppDestination {
    Map,
    Press,
    Collection,
}

private enum class DestinationIcon(
    val visualScale: Float,
) {
    Map(0.96f),
    Press(1.02f),
    Collection(1f),
}

@Composable
internal fun AppBottomNavigationBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val navigationFont = notoSansKrFontFamily()
    val hapticFeedback = LocalHapticFeedback.current
    val destinations = AppDestination.entries
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
        val itemWidth = maxWidth / destinations.size
        val indicatorInset = 5.dp
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedDestination.ordinal + indicatorInset,
            animationSpec = tween(240, easing = CubicBezierEasing(0f, 0f, 0.33f, 1f)),
            label = "mainNavigationIndicatorOffset",
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth - indicatorInset * 2)
                .height(AppBottomNavigationBarHeight - indicatorInset * 2)
                .clip(CircleShape)
                .background(AppColors.Primary, CircleShape)
                .border(1.5.dp, NavigationInk, CircleShape),
        )
        Row(Modifier.fillMaxWidth()) {
            destinations.forEach { destination ->
                val isSelected = destination == selectedDestination
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(AppBottomNavigationBarHeight)
                            .clickable(
                                enabled = enabled,
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDestinationSelected(destination)
                                },
                            ).semantics { selected = isSelected },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DestinationIconView(
                        icon =
                            when (destination) {
                                AppDestination.Map -> DestinationIcon.Map
                                AppDestination.Press -> DestinationIcon.Press
                                AppDestination.Collection -> DestinationIcon.Collection
                            },
                        pressScale = 1f,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text =
                            stringResource(
                                when (destination) {
                                    AppDestination.Map -> Res.string.bottom_navigation_map
                                    AppDestination.Press -> Res.string.bottom_navigation_press
                                    AppDestination.Collection -> Res.string.bottom_navigation_collection
                                },
                            ),
                        color = NavigationInk,
                        fontSize = 14.sp,
                        fontFamily = navigationFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
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
    if (icon == DestinationIcon.Collection) {
        Image(
            painter = painterResource(Res.drawable.nav_collection),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        return
    }
    if (icon == DestinationIcon.Press) {
        PressNavigationIcon(pressScale = icon.visualScale * pressScale)
        return
    }
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
                DestinationIcon.Press -> {}

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

                DestinationIcon.Collection -> {
                    val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawRect(
                        Color.White,
                        Offset(5f, 2f),
                        androidx.compose.ui.geometry
                            .Size(11f, 15f),
                    )
                    drawRect(
                        NavigationInk,
                        Offset(5f, 2f),
                        androidx.compose.ui.geometry
                            .Size(11f, 15f),
                        style = stroke,
                    )
                    drawLine(NavigationInk, Offset(3f, 5f), Offset(3f, 19f), 1.5f, cap = StrokeCap.Round)
                    drawLine(NavigationInk, Offset(3f, 19f), Offset(13f, 19f), 1.5f, cap = StrokeCap.Round)
                    drawLine(NavigationInk, Offset(8f, 6f), Offset(13f, 6f), 1.5f, cap = StrokeCap.Round)
                    drawLine(NavigationInk, Offset(8f, 9f), Offset(13f, 9f), 1.5f, cap = StrokeCap.Round)
                    drawLine(NavigationInk, Offset(8f, 12f), Offset(11f, 12f), 1.5f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun PressNavigationIcon(pressScale: Float) {
    Image(
        painter = painterResource(Res.drawable.nav_press),
        contentDescription = null,
        modifier =
            Modifier.size(20.dp).graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
    )
}

@Preview(name = "지도 선택")
@Composable
private fun AppBottomNavigationBarMapPreview() {
    AppBottomNavigationBar(AppDestination.Map, {})
}

@Preview(name = "프레스 선택")
@Composable
private fun AppBottomNavigationBarPressPreview() {
    AppBottomNavigationBar(AppDestination.Press, {})
}

@Preview(name = "큰 글씨", fontScale = 1.5f)
@Composable
private fun AppBottomNavigationBarLargeFontPreview() {
    AppBottomNavigationBar(AppDestination.Press, {})
}
