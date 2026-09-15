package com.pheeeew.feature.map.guide

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_hand

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

        FirstSighGuideBubble(
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
internal fun FirstSighGuideBubble(
    step: FirstSighGuideStep,
    modifier: Modifier = Modifier,
) {
    val page =
        when (step) {
            FirstSighGuideStep.TapButton -> 1
            FirstSighGuideStep.Memo -> 2
            FirstSighGuideStep.Blow -> 3
            FirstSighGuideStep.SwipeUp -> 4
            FirstSighGuideStep.Hidden -> return
        }
    val message =
        when (step) {
            FirstSighGuideStep.TapButton -> "빛나는 버튼을 눌러\n한숨을 시작해 보세요"
            FirstSighGuideStep.Memo -> "오늘 어떤 일이 있었는지\n한숨에 담아 보세요"
            FirstSighGuideStep.Blow -> "마이크에 대고 후- 하고\n한숨을 불어 보세요"
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
                    .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = AppColors.Blue200)
                    .background(AppColors.Navy700.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
                    .border(1.dp, AppColors.Blue200.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = "$page / 4",
                color = AppColors.Blue100,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.5.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
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
    Image(
        painter = painterResource(Res.drawable.ic_hand),
        contentDescription = null,
        modifier = modifier.size(width = 59.dp, height = 80.dp),
    )
}
