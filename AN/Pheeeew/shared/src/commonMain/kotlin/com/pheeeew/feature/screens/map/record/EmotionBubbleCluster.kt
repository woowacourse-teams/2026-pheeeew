package com.pheeeew.feature.screens.map.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_bubble
import pheeeew.shared.generated.resources.ic_close
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun EmotionBubbleCluster(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEmotionClick: (EmotionTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleLabel = if (isExpanded) "감정 다시 담기" else "비눗방울 눌러 감정 꺼내기"
    val idleProgress =
        if (isExpanded) {
            0.5f
        } else {
            val idleTransition = rememberInfiniteTransition(label = "emotionBubbleIdleMotion")
            val progress by
                idleTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "emotionBubbleIdleProgress",
                )
            progress
        }
    val idleMotion = if (isExpanded) 0f else 1f
    val idleCycle = idleProgress * 2f * PI.toFloat()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleX by
        animateFloatAsState(
            targetValue = if (isPressed) 1.035f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
            label = "emotionBubblePressScaleX",
        )
    val pressScaleY by
        animateFloatAsState(
            targetValue = if (isPressed) 0.82f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
            label = "emotionBubblePressScaleY",
        )
    val bubbleScaleX = 0.97f + 0.06f * idleProgress
    val bubbleScaleY = 1.03f - 0.06f * idleProgress
    val bubbleRotation = (idleProgress - 0.5f) * 1.4f * idleMotion
    val groupDriftX = sin(idleCycle) * 1.4f * idleMotion
    val groupDriftY = cos(idleCycle) * 1.8f * idleMotion

    Box(
        modifier =
            modifier
                .offset(x = groupDriftX.dp, y = groupDriftY.dp)
                .graphicsLayer {
                    scaleX = pressScaleX
                    scaleY = pressScaleY
                    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0.75f)
                }.size(width = 340.dp, height = 260.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = 114.dp, y = 140.dp)
                    .size(112.dp)
                    .semantics { contentDescription = toggleLabel }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = toggleLabel,
                        onClick = onToggle,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_bubble),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            scaleX = bubbleScaleX
                            scaleY = bubbleScaleY
                            rotationZ = bubbleRotation
                        },
            )
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(durationMillis = 180, delayMillis = 120)) + scaleIn(initialScale = 0.5f),
                exit = fadeOut(tween(durationMillis = 120)) + scaleOut(targetScale = 0.7f),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = "닫기",
                    modifier = Modifier.size(32.dp),
                    tint = Color.DarkGray,
                )
            }
        }

        EmotionTypeUiModel.entries.forEach { emotion ->
            EmotionBubble(
                emotion = emotion,
                isExpanded = isExpanded,
                idleProgress = idleProgress,
                onEmotionClick = onEmotionClick,
            )
        }
    }
}

@Composable
private fun EmotionBubble(
    emotion: EmotionTypeUiModel,
    isExpanded: Boolean,
    idleProgress: Float,
    onEmotionClick: (EmotionTypeUiModel) -> Unit,
) {
    val emotionCycle = idleProgress * 2f * PI.toFloat() + emotion.ordinal * 2f * PI.toFloat() / 5f
    val idleFloatX = if (isExpanded) 0f else sin(emotionCycle) * 0.9f
    val idleFloatY = if (isExpanded) 0f else cos(emotionCycle) * 0.9f
    val idleRotation = if (isExpanded) 0f else sin(emotionCycle) * 1.1f
    val animationDelay =
        if (isExpanded) {
            emotion.ordinal * 48
        } else {
            (EmotionTypeUiModel.entries.size - emotion.ordinal - 1) * 36
        }
    val animationSpec =
        tween<Dp>(
            durationMillis = 440,
            delayMillis = animationDelay,
            easing = FastOutSlowInEasing,
        )
    val targetOffset = emotion.offset(isExpanded)
    val x by
        animateDpAsState(
            targetValue = targetOffset.x,
            animationSpec = animationSpec,
            label = "emotionBubbleX",
        )
    val y by
        animateDpAsState(
            targetValue = targetOffset.y,
            animationSpec = animationSpec,
            label = "emotionBubbleY",
        )
    val iconSize by
        animateDpAsState(
            targetValue = if (isExpanded) 58.dp else 24.dp,
            animationSpec = animationSpec,
            label = "emotionBubbleSize",
        )
    Column(
        modifier =
            Modifier
                .offset(
                    x = x + idleFloatX.dp,
                    y = y + idleFloatY.dp,
                ).graphicsLayer {
                    rotationZ = idleRotation
                }.width(iconSize)
                .then(
                    if (isExpanded) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "${emotion.label} 선택",
                        ) { onEmotionClick(emotion) }
                    } else {
                        Modifier
                    },
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(emotion.icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter =
                fadeIn(
                    animationSpec =
                        tween(
                            durationMillis = 180,
                            delayMillis = 150 + emotion.ordinal * 48,
                        ),
                ) +
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(durationMillis = 180, delayMillis = 150 + emotion.ordinal * 48),
                    ),
            exit =
                fadeOut(
                    tween(durationMillis = 100),
                ) + shrinkVertically(animationSpec = tween(durationMillis = 100)),
        ) {
            Text(
                text = emotion.label,
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style =
                    TextStyle(
                        shadow =
                            Shadow(
                                color = Color.White,
                                offset = Offset.Zero,
                                blurRadius = 10f,
                            ),
                    ),
            )
        }
    }
}

private fun EmotionTypeUiModel.offset(isExpanded: Boolean): DpOffset =
    if (isExpanded) {
        DpOffset(x = (3 + ordinal * 67).dp, y = 12.dp)
    } else {
        when (this) {
            EmotionTypeUiModel.Stuck -> DpOffset(x = 158.dp, y = 154.dp)
            EmotionTypeUiModel.Annoyed -> DpOffset(x = 186.dp, y = 175.dp)
            EmotionTypeUiModel.Exhausted -> DpOffset(x = 176.dp, y = 208.dp)
            EmotionTypeUiModel.Frustrated -> DpOffset(x = 140.dp, y = 208.dp)
            EmotionTypeUiModel.Angry -> DpOffset(x = 130.dp, y = 175.dp)
        }
    }

@Preview(name = "접힌 감정 비눗방울", widthDp = 402, heightDp = 320, showBackground = true)
@Composable
fun EmotionBubbleClusterCollapsedPreview() {
    Box(
        modifier = Modifier.size(width = 402.dp, height = 320.dp).background(Color(0xFFECEAE5)),
        contentAlignment = Alignment.Center,
    ) {
        EmotionBubbleCluster(isExpanded = false, onToggle = {}, onEmotionClick = {})
    }
}

@Preview(name = "펼친 감정 비눗방울", widthDp = 402, heightDp = 320, showBackground = true)
@Composable
fun EmotionBubbleClusterExpandedPreview() {
    Box(
        modifier = Modifier.size(width = 402.dp, height = 320.dp).background(Color(0xffc5e4b7)),
        contentAlignment = Alignment.Center,
    ) {
        EmotionBubbleCluster(isExpanded = true, onToggle = {}, onEmotionClick = {})
    }
}
