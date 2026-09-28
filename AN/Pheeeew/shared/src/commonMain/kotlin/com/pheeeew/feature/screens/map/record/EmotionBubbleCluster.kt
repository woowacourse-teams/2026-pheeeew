package com.pheeeew.feature.screens.map.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_close
import kotlin.time.TimeSource

private const val BUBBLE_PRESS_MILLIS = 65
private const val BUBBLE_MINIMUM_PRESS_MILLIS = 84L
private const val BUBBLE_SPRING_DAMPING_RATIO = 0.4455f
private const val BUBBLE_SPRING_STIFFNESS = 861.5385f
private const val EMOTION_SPRING_DAMPING_RATIO = 0.5443f
private const val EMOTION_SPRING_STIFFNESS = 527.7778f
private const val EMOTION_INTERACTION_DAMPING_RATIO = 0.4694f
private const val EMOTION_INTERACTION_STIFFNESS = 600f
private val BUBBLE_EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EMOTION_EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val EMOTION_POSITION_SPRING =
    spring<Float>(
        dampingRatio = EMOTION_SPRING_DAMPING_RATIO,
        stiffness = EMOTION_SPRING_STIFFNESS,
        visibilityThreshold = 0.01f,
    )
private val EMOTION_SCALE_SPRING =
    spring<Float>(
        dampingRatio = EMOTION_SPRING_DAMPING_RATIO,
        stiffness = EMOTION_SPRING_STIFFNESS,
        visibilityThreshold = 0.001f,
    )
private val EMOTION_INTERACTION_SPRING =
    spring<Float>(
        dampingRatio = EMOTION_INTERACTION_DAMPING_RATIO,
        stiffness = EMOTION_INTERACTION_STIFFNESS,
        visibilityThreshold = 0.001f,
    )

@Composable
internal fun EmotionBubbleCluster(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEmotionClick: (EmotionTypeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleLabel = if (isExpanded) "감정 다시 담기" else "비눗방울 눌러 감정 꺼내기"
    val interactionSource = remember { MutableInteractionSource() }
    val isHeld by interactionSource.collectIsPressedAsState()
    var isPressed by remember { mutableStateOf(false) }
    var pressStartedAt by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    LaunchedEffect(isHeld) {
        if (isHeld) {
            pressStartedAt = TimeSource.Monotonic.markNow()
            isPressed = true
        } else {
            val remainingPressMillis =
                (BUBBLE_MINIMUM_PRESS_MILLIS - pressStartedAt.elapsedNow().inWholeMilliseconds)
                    .coerceAtLeast(0)
            if (remainingPressMillis > 0) delay(remainingPressMillis)
            isPressed = false
        }
    }
    val pressAnimation =
        if (isPressed) {
            tween<Float>(durationMillis = BUBBLE_PRESS_MILLIS, easing = BUBBLE_EASE_OUT)
        } else {
            spring(
                dampingRatio = BUBBLE_SPRING_DAMPING_RATIO,
                stiffness = BUBBLE_SPRING_STIFFNESS,
                visibilityThreshold = 0.001f,
            )
        }
    val pressScaleX by
        animateFloatAsState(
            targetValue = if (isPressed) 1.13f else 1f,
            animationSpec = pressAnimation,
            label = "emotionBubblePressScaleX",
        )
    val pressScaleY by
        animateFloatAsState(
            targetValue = if (isPressed) 0.82f else 1f,
            animationSpec = pressAnimation,
            label = "emotionBubblePressScaleY",
        )
    val pressTranslationY by
        animateFloatAsState(
            targetValue = if (isPressed) 9f else 0f,
            animationSpec = pressAnimation,
            label = "emotionBubblePressTranslationY",
        )
    val rippleProgress = remember { Animatable(1f) }
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = BUBBLE_EASE_OUT),
            )
        }
    }
    val closeProgress by
        animateFloatAsState(
            targetValue = if (isExpanded) 1f else 0f,
            animationSpec = tween(durationMillis = 150),
            label = "emotionBubbleCloseProgress",
        )

    Box(
        modifier =
            modifier
                .size(width = 340.dp, height = 260.dp),
    ) {
        if (isExpanded && rippleProgress.value < 1f) {
            Canvas(
                modifier =
                    Modifier
                        .offset(x = 114.dp, y = 140.dp)
                        .size(112.dp),
            ) {
                drawCircle(
                    color = Color(0xA6F7FFFF).copy(alpha = (1f - rippleProgress.value) * 0.6f * (166f / 255f)),
                    radius = size.width / 2f * (0.72f + rippleProgress.value * 0.78f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .offset(x = 114.dp, y = 140.dp)
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = pressScaleX
                        scaleY = pressScaleY
                        translationY = pressTranslationY.dp.toPx()
                        transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0.7f)
                    }.semantics {
                        contentDescription = toggleLabel
                        stateDescription = if (isExpanded) "펼침" else "닫힘"
                    }.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = toggleLabel,
                        onClick = onToggle,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val canvasScale = size.width / 112f
                scale(canvasScale, canvasScale, Offset.Zero) {
                    val center = Offset(56f, 56f)
                    drawCircle(Color(0x24EAFBFF), 55f, center)
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(Color(0xACFFFFFF), Color(0x48FFFFFF), Color.Transparent),
                            center = Offset(30f, 24f),
                            radius = 54f,
                        ),
                        55f,
                        center,
                    )
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(Color(0x4495E3F0), Color.Transparent),
                            center = Offset(25f, 91f),
                            radius = 70f,
                        ),
                        55f,
                        center,
                    )
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(Color(0x30FFB8E3), Color.Transparent),
                            center = Offset(90f, 76f),
                            radius = 74f,
                        ),
                        55f,
                        center,
                    )
                    drawCircle(Color(0x537699A1), 55.3f, center, style = Stroke(0.65f))
                    rotate(-70f, center) {
                        drawCircle(
                            brush =
                                Brush.sweepGradient(
                                    0f to Color(0xFFF9FFFF),
                                    0.02f to Color(0xFFF9FFFF),
                                    0.17f to Color(0xFFA7D8F5),
                                    0.29f to Color(0xFFD6CBFF),
                                    0.42f to Color(0xFFFFDDDD),
                                    0.5f to Color(0xFFD9D9FF),
                                    0.6f to Color(0xFFA5DDEF),
                                    0.72f to Color(0xFFE4FFDD),
                                    0.86f to Color(0xFFD8FBFF),
                                    0.96f to Color.White,
                                    1f to Color.White,
                                    center = center,
                                ),
                            radius = 54.5f,
                            center = center,
                            alpha = 0.94f,
                            style = Stroke(3f),
                        )
                    }
                    rotate(-17f, center) {
                        listOf(
                            Color(0x99FFFFFF) to 135f,
                            Color(0x99FFFFFF) to 225f,
                            Color(0x4DC0CEFF) to -45f,
                            Color(0x12FFFFFF) to 45f,
                        ).forEach { (color, startAngle) ->
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(5f, 5f),
                                size = Size(102f, 102f),
                                style = Stroke(1.4f),
                            )
                        }
                    }
                    drawCircle(Color(0x85EFFAFF), 59f, center, style = Stroke(0.7f))
                    rotate(-42f, Offset(31.5f, 15f)) {
                        drawOval(Color.White.copy(alpha = 0.92f), Offset(16f, 11f), Size(31f, 8f))
                    }
                    rotate(15f, Offset(12.5f, 38.5f)) {
                        drawOval(Color.White.copy(alpha = 0.75f), Offset(10f, 31f), Size(5f, 15f))
                    }
                    rotate(-44f, Offset(85.5f, 93.5f)) {
                        drawOval(Color.White.copy(alpha = 0.83f), Offset(71f, 91f), Size(29f, 5f))
                    }
                }
            }
            if (closeProgress > 0f) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = "닫기",
                    modifier =
                        Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                alpha = closeProgress
                                scaleX = 0.6f + 0.4f * closeProgress
                                scaleY = scaleX
                            },
                    tint = Color(0xFF6A7778),
                )
            }
        }

        EmotionTypeUiModel.entries.forEach { emotion ->
            EmotionBubble(
                emotion = emotion,
                isExpanded = isExpanded,
                isBubblePressed = isPressed,
                onEmotionClick = onEmotionClick,
            )
        }
    }
}

@Composable
private fun EmotionBubble(
    emotion: EmotionTypeUiModel,
    isExpanded: Boolean,
    isBubblePressed: Boolean,
    onEmotionClick: (EmotionTypeUiModel) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val interactionScale by
        animateFloatAsState(
            targetValue =
                if (isPressed) {
                    0.86f
                } else if (isHovered && isExpanded) {
                    1.13f
                } else {
                    1f
                },
            animationSpec = EMOTION_INTERACTION_SPRING,
            label = "emotionFaceInteractionScale",
        )
    val faceHoverY by
        animateFloatAsState(
            targetValue = if (isHovered && isExpanded) -3f else 0f,
            animationSpec = EMOTION_INTERACTION_SPRING,
            label = "emotionFaceHoverOffset",
        )
    val baseOffset = emotion.offset(isExpanded)
    val targetOffset =
        baseOffset.copy(
            y = baseOffset.y + if (!isExpanded && isBubblePressed) 4.dp else 0.dp,
        )
    val targetScale = if (isExpanded) 1f else 32f / 52f
    val x = remember { Animatable(targetOffset.x.value) }
    val y = remember { Animatable(targetOffset.y.value) }
    val scale = remember { Animatable(targetScale) }
    val velocities = remember { floatArrayOf(0f, 0f, 0f) }
    val animationDelayMillis =
        if (isExpanded) {
            75L + emotion.ordinal * 65L
        } else {
            (EmotionTypeUiModel.entries.lastIndex - emotion.ordinal) * 18L
        }
    LaunchedEffect(targetOffset, targetScale, isExpanded) {
        delay(animationDelayMillis)
        coroutineScope {
            launch {
                x.animateTo(targetOffset.x.value, EMOTION_POSITION_SPRING, initialVelocity = velocities[0]) {
                    velocities[0] = velocity
                }
                velocities[0] = 0f
            }
            launch {
                y.animateTo(targetOffset.y.value, EMOTION_POSITION_SPRING, initialVelocity = velocities[1]) {
                    velocities[1] = velocity
                }
                velocities[1] = 0f
            }
            launch {
                scale.animateTo(
                    targetValue = targetScale,
                    animationSpec = EMOTION_SCALE_SPRING,
                    initialVelocity = velocities[2],
                ) {
                    velocities[2] = velocity
                }
                velocities[2] = 0f
            }
        }
    }
    var floating by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            floating = 0f
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (startNanos == 0L) startNanos = frameNanos
                val elapsedSeconds = (frameNanos - startNanos).toDouble() / 1_000_000_000.0
                val phase =
                    ((elapsedSeconds + emotion.ordinal * 0.79) / (3.2 + emotion.ordinal * 0.29) % 1.0)
                        .toFloat()
                floating =
                    if (phase < 0.5f) {
                        EMOTION_EASE_IN_OUT.transform(phase * 2f)
                    } else {
                        1f - EMOTION_EASE_IN_OUT.transform((phase - 0.5f) * 2f)
                    }
            }
        }
    }
    val labelAlpha by
        animateFloatAsState(
            targetValue = if (isExpanded) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = 140,
                    delayMillis = if (isExpanded) 240 + emotion.ordinal * 55 else 0,
                ),
            label = "emotionBubbleLabelAlpha",
        )
    Box(
        modifier =
            Modifier
                .offset(
                    x = x.value.dp,
                    y = y.value.dp,
                ).size(width = 52.dp, height = 76.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painter = painterResource(emotion.icon),
            contentDescription = null,
            modifier =
                Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        val scaleCompensation = (1f - scale.value) * 26f
                        translationX = -scaleCompensation.dp.toPx() + floating.dp.toPx()
                        translationY =
                            -scaleCompensation.dp.toPx() -
                            (5f * floating).dp.toPx() +
                            faceHoverY.dp.toPx()
                        scaleX = scale.value * interactionScale
                        scaleY = scale.value * interactionScale
                        rotationZ = if (isExpanded) 0f else -3f + floating * 7f
                    }.clip(CircleShape)
                    .hoverable(interactionSource, enabled = isExpanded)
                    .then(
                        if (isExpanded) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "${emotion.label} 선택",
                            ) { onEmotionClick(emotion) }
                        } else {
                            Modifier
                        },
                    ),
        )
        if (labelAlpha > 0f) {
            Text(
                text = emotion.label,
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .offset(y = 57.dp)
                        .width(72.dp)
                        .graphicsLayer { alpha = labelAlpha },
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
        DpOffset(x = (3 + ordinal * 67).dp, y = 53.dp)
    } else {
        when (this) {
            EmotionTypeUiModel.FRUSTRATED -> DpOffset(x = 127.dp, y = 180.dp)
            EmotionTypeUiModel.IRRITATED -> DpOffset(x = 156.dp, y = 173.dp)
            EmotionTypeUiModel.EXHAUSTED -> DpOffset(x = 185.dp, y = 180.dp)
            EmotionTypeUiModel.DISCOURAGED -> DpOffset(x = 141.dp, y = 207.dp)
            EmotionTypeUiModel.ANGRY -> DpOffset(x = 171.dp, y = 207.dp)
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
