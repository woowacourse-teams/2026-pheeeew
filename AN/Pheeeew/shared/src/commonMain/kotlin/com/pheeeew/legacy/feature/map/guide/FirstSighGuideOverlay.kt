package com.pheeeew.legacy.feature.map.guide

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_hand

@Composable
fun FirstSighGuideOverlay(
    step: FirstSighGuideStep,
    controlBoundsInRoot: Rect,
    controlAnchorBottomInRoot: Float? = null,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (step == FirstSighGuideStep.Hidden) return

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val liveTargetTop =
            if (controlBoundsInRoot.height > 0f) {
                with(density) { controlBoundsInRoot.top.toDp() }
            } else {
                maxHeight - 132.dp
            }
        val maxControlDiameter = (maxWidth * 0.88f).coerceAtMost(360.dp)
        val navigationBarBottomInset =
            with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val fixedControlBottom =
            controlAnchorBottomInRoot
                ?.takeIf { it > 0f }
                ?: controlBoundsInRoot.takeIf { it.height > 0f }?.bottom
        val targetTop =
            if (
                step == FirstSighGuideStep.Blow ||
                step == FirstSighGuideStep.SwipeUp
            ) {
                fixedControlBottom
                    ?.let { bottom -> with(density) { bottom.toDp() } - maxControlDiameter }
                    ?: (
                        maxHeight -
                            navigationBarBottomInset -
                            maxControlDiameter -
                            BREATH_CONTROL_RESTING_BOTTOM_GAP
                    )
            } else {
                liveTargetTop
            }
        val bubbleGap =
            if (
                step == FirstSighGuideStep.Blow ||
                step == FirstSighGuideStep.SwipeUp
            ) {
                BREATH_GUIDE_BUBBLE_GAP
            } else {
                DEFAULT_GUIDE_BUBBLE_GAP
            }
        val bubbleBottomPadding =
            (maxHeight - targetTop + bubbleGap).coerceIn(132.dp, maxHeight - 112.dp)

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
    }
}

@Composable
fun SighSwipeHintOverlay(
    controlBoundsInRoot: Rect,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val transition = rememberInfiniteTransition(label = "sighSwipeHint")
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
                    label = "sighSwipeHintProgress",
                ).value
        val fingerAlpha =
            when {
                progress < 0.15f -> progress / 0.15f
                progress > 0.82f -> (1f - progress) / 0.18f
                else -> 1f
            }.coerceIn(0f, 1f)
        val density = LocalDensity.current
        val targetTop =
            if (controlBoundsInRoot.height > 0f) {
                with(density) { controlBoundsInRoot.top.toDp() }
            } else {
                maxHeight - 132.dp
            }
        val targetHeight =
            if (controlBoundsInRoot.height > 0f) {
                with(density) { controlBoundsInRoot.height.toDp() }
            } else {
                124.dp
            }
        val fingerStartY = targetTop + targetHeight * SWIPE_FINGER_START_FRACTION

        SwipeFinger(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(
                        x = SWIPE_FINGER_WIDTH / 2,
                        y = fingerStartY - SWIPE_FINGER_TRAVEL_DISTANCE * progress,
                    ).alpha(fingerAlpha),
        )
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
private fun SwipeFinger(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.ic_hand),
        contentDescription = null,
        modifier = modifier.size(width = SWIPE_FINGER_WIDTH, height = SWIPE_FINGER_HEIGHT),
    )
}

private const val SWIPE_FINGER_START_FRACTION = 0.375f
private val BREATH_GUIDE_BUBBLE_GAP = 60.dp
private val BREATH_CONTROL_RESTING_BOTTOM_GAP = 20.dp
private val DEFAULT_GUIDE_BUBBLE_GAP = 14.dp
private val SWIPE_FINGER_WIDTH = 59.dp
private val SWIPE_FINGER_HEIGHT = 80.dp
private val SWIPE_FINGER_TRAVEL_DISTANCE = 240.dp
