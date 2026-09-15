package com.pheeeew.feature.map.guide

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme

@Composable
fun FirstSighGuideOverlay(
    step: FirstSighGuideStep,
    controlBoundsInRoot: Rect,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (step == FirstSighGuideStep.Hidden) return

    BoxWithConstraints(modifier = modifier) {
        val targetTop =
            if (controlBoundsInRoot.height > 0f) {
                with(androidx.compose.ui.platform.LocalDensity.current) { controlBoundsInRoot.top.toDp() }
            } else {
                maxHeight - 132.dp
            }
        val swipeGuideSpacing = if (step == FirstSighGuideStep.SwipeUp) 124.dp else 0.dp
        val bubbleBottomPadding =
            (maxHeight - targetTop + 14.dp + swipeGuideSpacing).coerceIn(132.dp, maxHeight - 112.dp)

        Text(
            text = "건너뛰기",
            color = AppColors.Cream100,
            style = AppTheme.typography.menuItem.copy(fontSize = 18.sp),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(vertical = 30.dp, horizontal = 20.dp)
                    .clickable(
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkip,
                    ).padding(horizontal = 8.dp, vertical = 6.dp),
        )

        GuideBubble(
            step = step,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bubbleBottomPadding),
        )

        if (step == FirstSighGuideStep.SwipeUp) {
            SwipeUpAnimation(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (maxHeight - targetTop - 8.dp).coerceAtLeast(54.dp)),
            )
        }
    }
}

@Composable
private fun GuideBubble(
    step: FirstSighGuideStep,
    modifier: Modifier = Modifier,
) {
    val page =
        when (step) {
            FirstSighGuideStep.TapButton -> 1
            FirstSighGuideStep.Blow -> 2
            FirstSighGuideStep.SwipeUp -> 3
            FirstSighGuideStep.Hidden -> return
        }
    val message =
        when (step) {
            FirstSighGuideStep.TapButton -> "빛나는 버튼을 눌러\n한숨을 시작해 보세요"
            FirstSighGuideStep.Blow -> "휴대폰에 후- 하고\n한숨을 내쉬어 보세요"
            FirstSighGuideStep.SwipeUp -> "모인 한숨을 위로 밀어\n날려보내요"
            FirstSighGuideStep.Hidden -> return
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .width(190.dp)
                    .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = AppColors.Blue200)
                    .background(AppColors.Navy700.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                    .border(1.dp, AppColors.Blue200.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = "$page / 3",
                color = AppColors.Blue100,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = message,
                color = AppColors.Cream100,
                style = AppTheme.typography.menuItem,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                letterSpacing = 0.6.sp,
            )
        }
        Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
            val triangle =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
            drawPath(triangle, AppColors.Navy700)
        }
    }
}

@Composable
private fun SwipeUpAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "firstSighSwipeGuide")
    val progress =
        transition
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1_250, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "firstSighSwipeProgress",
            ).value
    val fingerAlpha =
        when {
            progress < 0.15f -> progress / 0.15f
            progress > 0.82f -> (1f - progress) / 0.18f
            else -> 1f
        }.coerceIn(0f, 1f)

    Box(modifier = modifier.size(width = 78.dp, height = 126.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val startY = size.height * 0.72f
            val endY = size.height * 0.16f
            drawLine(
                brush =
                    Brush.verticalGradient(
                        0f to AppColors.Blue100.copy(alpha = 0.85f),
                        1f to AppColors.Blue200.copy(alpha = 0.08f),
                        startY = endY,
                        endY = startY,
                    ),
                start = Offset(centerX, startY),
                end = Offset(centerX, endY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = AppColors.Blue100.copy(alpha = 0.9f),
                start = Offset(centerX, endY),
                end = Offset(centerX - 8.dp.toPx(), endY + 10.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = AppColors.Blue100.copy(alpha = 0.9f),
                start = Offset(centerX, endY),
                end = Offset(centerX + 8.dp.toPx(), endY + 10.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to AppColors.Blue100.copy(alpha = 0.32f),
                        1f to Color.Transparent,
                        center = Offset(centerX, endY),
                        radius = 24.dp.toPx(),
                    ),
                radius = 24.dp.toPx(),
                center = Offset(centerX, endY),
            )
        }

        SwipeFinger(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-progress * 70f).dp)
                    .alpha(fingerAlpha),
        )
    }
}

@Composable
private fun SwipeFinger(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 48.dp, height = 64.dp)) {
        val sx = size.width / 48f
        val sy = size.height / 64f

        fun x(value: Float) = value * sx

        fun y(value: Float) = value * sy

        val hand =
            Path().apply {
                moveTo(x(22f), y(3f))
                cubicTo(x(18f), y(3f), x(16f), y(6f), x(16f), y(11f))
                lineTo(x(16f), y(33f))
                lineTo(x(12f), y(28f))
                cubicTo(x(9f), y(24f), x(4f), y(27f), x(6f), y(32f))
                lineTo(x(15f), y(49f))
                cubicTo(x(18f), y(56f), x(24f), y(60f), x(32f), y(60f))
                cubicTo(x(40f), y(60f), x(44f), y(53f), x(44f), y(45f))
                lineTo(x(44f), y(30f))
                cubicTo(x(44f), y(26f), x(40f), y(24f), x(37f), y(27f))
                lineTo(x(37f), y(25f))
                cubicTo(x(37f), y(21f), x(32f), y(20f), x(29f), y(23f))
                lineTo(x(29f), y(11f))
                cubicTo(x(29f), y(6f), x(26f), y(3f), x(22f), y(3f))
                close()
            }
        drawPath(hand, AppColors.Navy700.copy(alpha = 0.88f), style = Fill)
        drawPath(
            hand,
            AppColors.Blue100,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
