package com.pheeeew.feature.map.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.core.audio.BreathInputError
import com.pheeeew.core.audio.rememberBreathInput
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.map.breath.BreathControlState
import com.pheeeew.feature.map.breath.BreathInteractionConfig
import com.pheeeew.feature.map.breath.BreathSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val BURST_DURATION_MILLIS = 720L
private const val IDLE_SCALE = 2f / 3f
private const val NEEDS_MORE_HINT_MILLIS = 1_400L
private const val RELEASE_DRAG_MAX_DP = 1200f
private const val FLING_VELOCITY_THRESHOLD_DP = 250f
private const val SHAKE_AMPLITUDE_DP = 3f
private const val FLY_AWAY_DISTANCE_DP = 1600f
private const val IDLE_TEXT_GAP_BOX_HEIGHT_DP = 100f
private const val BUTTON_BOTTOM_MARGIN_DP = 32f

private val DEFAULT_BREATH_CONFIG = BreathInteractionConfig()

enum class SighPhase { Idle, Listening, Quiet, NeedsMore, Bursting }

@Composable
fun BreathControl(
    enabled: Boolean,
    onExplosionFinished: (originInRoot: Offset) -> Unit,
    onMicrophoneError: (BreathInputError) -> Unit,
    ensureLocationPermission: suspend () -> Boolean,
    onPhaseChanged: (SighPhase) -> Unit,
    cancelSignal: Int,
    requestPermissionOnLaunch: Boolean,
    modifier: Modifier = Modifier,
) {
    var burstProgress by remember { mutableStateOf(0f) }
    var needsMoreActive by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var burstOrigin by remember { mutableStateOf(Offset.Zero) }
    val dragOffsetY = remember { Animatable(0f) }
    val breathInput = rememberBreathInput()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latestExplosionFinished = rememberUpdatedState(onExplosionFinished)
    val latestMicrophoneError = rememberUpdatedState(onMicrophoneError)
    val latestPhaseChanged = rememberUpdatedState(onPhaseChanged)
    val latestEnsureLocationPermission = rememberUpdatedState(ensureLocationPermission)
    val breathControlState =
        remember(breathInput, lifecycleOwner) {
            BreathControlState(
                breathInput = breathInput,
                scope = coroutineScope,
                ensureLocationPermission = { latestEnsureLocationPermission.value() },
            )
        }
    val sessionState by breathControlState.session.collectAsState()
    val needsMoreRevision by breathControlState.needsMoreRevision.collectAsState()
    val burstRevision by breathControlState.burstRevision.collectAsState()
    val listening = sessionState.isInputActive()
    val strength = sessionState.strength
    val growth = sessionState.growth
    val quietForMillis = sessionState.quietFor.inWholeMilliseconds
    val burst = sessionState is BreathSessionState.Bursting
    val sessionPhase =
        when (sessionState) {
            is BreathSessionState.Bursting -> SighPhase.Bursting
            is BreathSessionState.Quiet -> SighPhase.Quiet
            is BreathSessionState.Listening -> SighPhase.Listening
            is BreathSessionState.NeedsMore -> SighPhase.NeedsMore
            else -> SighPhase.Idle
        }

    LaunchedEffect(breathControlState, requestPermissionOnLaunch) {
        if (requestPermissionOnLaunch) {
            breathControlState.warmUpMicrophonePermission()
        }
    }

    LaunchedEffect(breathControlState) {
        breathControlState.errors.collect { error -> latestMicrophoneError.value(error) }
    }

    DisposableEffect(breathControlState, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    breathControlState.lifecycleStopped()
                    coroutineScope.launch { dragOffsetY.snapTo(0f) }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            breathControlState.dispose()
        }
    }

    LaunchedEffect(burstRevision, sessionState.sessionId, sessionPhase) {
        if (burstRevision == 0L || sessionPhase != SighPhase.Bursting) return@LaunchedEffect
        val sessionId = (sessionState as? BreathSessionState.Bursting)?.sessionId ?: return@LaunchedEffect
        burstProgress = 0f
        val start =
            kotlin.time.TimeSource.Monotonic
                .markNow()
        val dragAnimation =
            launch {
                dragOffsetY.animateTo(
                    targetValue = -with(density) { FLY_AWAY_DISTANCE_DP.dp.toPx() },
                    animationSpec =
                        tween(
                            durationMillis = BURST_DURATION_MILLIS.toInt(),
                            easing = FastOutLinearInEasing,
                        ),
                )
            }
        while (burstProgress < 1f) {
            delay(16)
            burstProgress = (start.elapsedNow().inWholeMilliseconds.toFloat() / BURST_DURATION_MILLIS).coerceIn(0f, 1f)
        }
        dragAnimation.join()
        if (breathControlState.session.value != BreathSessionState.Bursting(sessionId)) {
            return@LaunchedEffect
        }
        breathControlState.burstFinished(sessionId)
        breathControlState.session
            .filter { it == BreathSessionState.Idle(sessionId) }
            .first()
        latestExplosionFinished.value(burstOrigin)
        burstProgress = 0f
        dragOffsetY.snapTo(0f)
    }

    LaunchedEffect(needsMoreRevision, sessionState.sessionId, sessionPhase) {
        if (needsMoreRevision == 0L || sessionPhase != SighPhase.NeedsMore) return@LaunchedEffect
        val sessionId = sessionState.sessionId
        if (sessionState !is BreathSessionState.NeedsMore) return@LaunchedEffect
        needsMoreActive = true
        dragOffsetY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
        delay(NEEDS_MORE_HINT_MILLIS)
        if (breathControlState.session.value.sessionId == sessionId &&
            breathControlState.session.value is BreathSessionState.NeedsMore
        ) {
            needsMoreActive = false
        }
    }

    LaunchedEffect(cancelSignal) {
        if (cancelSignal > 0) {
            breathControlState.cancel()
            dragOffsetY.snapTo(0f)
        }
    }

    val phase =
        when {
            burst -> SighPhase.Bursting
            !listening -> SighPhase.Idle
            needsMoreActive && sessionState is BreathSessionState.NeedsMore -> SighPhase.NeedsMore
            growth >= 1f -> SighPhase.Quiet
            growth > 0f && quietForMillis >= DEFAULT_BREATH_CONFIG.quietDelay.inWholeMilliseconds -> SighPhase.Quiet
            else -> SighPhase.Listening
        }
    LaunchedEffect(phase) { latestPhaseChanged.value(phase) }

    // TODO
    val controlDescription =
        when {
            burst -> "한숨을 별로 만드는 중"
            listening -> "한숨 감지 중. 위로 슬라이드하면 별을 날려보냅니다"
            else -> "한숨 감지 시작"
        }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!listening && !burst) {
            Text(
                text = "한숨 내쉬기",
                style = AppTheme.typography.menuItem,
                color = AppColors.Cream100,
            )
        }
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = BUTTON_BOTTOM_MARGIN_DP.dp)
                    .height(IDLE_TEXT_GAP_BOX_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            val maxDiameter = (maxWidth.value * 0.88f).coerceAtMost(360f)
            val maxScale = (maxDiameter / 124f).coerceAtLeast(1f)
            val targetScale =
                when {
                    burst -> 1f
                    listening -> 1f + growth * (maxScale - 1f)
                    else -> IDLE_SCALE
                }
            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec =
                    if (burst) {
                        // Match the fly-away's duration and easing exactly so the shrink and the upward
                        // launch complete in lockstep instead of the shrink finishing early and leaving
                        // the button sitting there, barely moved, until the (slower-starting) flight catches up.
                        tween(durationMillis = BURST_DURATION_MILLIS.toInt(), easing = FastOutLinearInEasing)
                    } else {
                        spring(dampingRatio = 0.82f, stiffness = 180f)
                    },
                label = "breathScale",
            )
            val particleDiameter = (maxWidth.value * 1.6f).coerceAtMost(680f)

            val isMaxedAndBlowing =
                listening &&
                    growth >= 1f &&
                    strength >= DEFAULT_BREATH_CONFIG.effectiveStrengthThreshold
            val shakePhase by rememberInfiniteTransition(label = "maxShake").animateFloat(
                initialValue = -1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Reverse),
                label = "maxShakeValue",
            )
            val shakeOffsetPx =
                if (isMaxedAndBlowing) {
                    shakePhase *
                        with(
                            density,
                        ) { SHAKE_AMPLITUDE_DP.dp.toPx() }
                } else {
                    0f
                }
            // Center alignment keeps the box's center point fixed as it resizes; shifting it up by
            // half the size delta from the 124dp reference makes the bottom edge stay put instead,
            // so the button visually grows upward from a fixed base rather than from its center.
            val bottomAnchorShiftPx = with(density) { (62f * (1f - scale)).dp.toPx() }

            Box(
                modifier =
                    Modifier
                        .offset {
                            IntOffset(
                                shakeOffsetPx.roundToInt(),
                                (bottomAnchorShiftPx + dragOffsetY.value).roundToInt(),
                            )
                        }.requiredSize(124.dp * scale)
                        .onGloballyPositioned { coordinates ->
                            val point = coordinates.positionInRoot()
                            origin =
                                Offset(point.x + coordinates.size.width / 2f, point.y + coordinates.size.height / 2f)
                        }.pointerInput(enabled, listening, burst) {
                            if (!listening) {
                                detectTapGestures(onTap = {
                                    if (!enabled || burst) return@detectTapGestures
                                    breathControlState.start()
                                })
                            } else {
                                val maxDragPx = with(density) { RELEASE_DRAG_MAX_DP.dp.toPx() }
                                val flingThresholdPx = with(density) { FLING_VELOCITY_THRESHOLD_DP.dp.toPx() }
                                // Manual velocity calc (whole-gesture average) instead of Compose's
                                // VelocityTracker: on iOS this app's fling detection was unreliable using
                                // VelocityTracker, so this avoids depending on its platform-specific internal
                                // smoothing. Uses the raw per-event dragAmount deltas summed up, NOT
                                // change.position — position is in the pointerInput node's own (moving)
                                // local coordinate frame, and since this box translates to follow the
                                // finger, the local position barely changes even while the finger travels
                                // far, which silently corrupts any position-delta-based velocity calc.
                                var dragStartTimeMillis = -1L
                                var lastDragTimeMillis = 0L
                                var rawTraveledY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        dragStartTimeMillis = -1L
                                        rawTraveledY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (dragStartTimeMillis < 0L) {
                                            dragStartTimeMillis = change.uptimeMillis
                                        }
                                        lastDragTimeMillis = change.uptimeMillis
                                        rawTraveledY += dragAmount.y
                                        coroutineScope.launch {
                                            dragOffsetY.snapTo(
                                                (dragOffsetY.value + dragAmount.y).coerceIn(-maxDragPx, 0f),
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        // A slow, deliberate raise must NOT register — only a fast upward
                                        // flick (fling) does, regardless of how far the drag itself traveled.
                                        val elapsedMillis = (lastDragTimeMillis - dragStartTimeMillis).coerceAtLeast(1L)
                                        val flingVelocityY = rawTraveledY / elapsedMillis * 1000f
                                        when {
                                            flingVelocityY > -flingThresholdPx -> {
                                                coroutineScope.launch {
                                                    dragOffsetY.animateTo(
                                                        0f,
                                                        spring(dampingRatio = 0.7f, stiffness = 300f),
                                                    )
                                                }
                                            }

                                            else -> {
                                                burstOrigin = origin
                                                val upwardDistanceDp =
                                                    with(density) {
                                                        (-rawTraveledY).coerceAtLeast(0f).toDp().value
                                                    }
                                                val upwardVelocityDpPerSecond =
                                                    with(density) {
                                                        flingVelocityY.toDp().value
                                                    }
                                                breathControlState.release(
                                                    upwardDistanceDp = upwardDistanceDp,
                                                    upwardVelocityDpPerSecond = upwardVelocityDpPerSecond,
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            dragOffsetY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 300f))
                                        }
                                    },
                                )
                            }
                        }.semantics {
                            role = Role.Button
                            contentDescription = controlDescription
                        },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.requiredSize(particleDiameter.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = 62.dp.toPx() * scale
                    val burstEase = 1f - (1f - burstProgress) * (1f - burstProgress) * (1f - burstProgress)
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                0.0f to AppColors.Cream100,
                                0.35f to AppColors.Tan200,
                                0.7f to AppColors.Blue100,
                                1.0f to AppColors.Blue200.copy(alpha = 0f),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                    )
                    if (burstProgress > 0f) {
                        drawCircle(
                            Color.White.copy(alpha = (1f - burstProgress * 2.4f).coerceAtLeast(0f)),
                            radius * (0.4f + burstEase),
                            center,
                        )
                        repeat(18) { index ->
                            val angle = index * (2f * PI.toFloat() / 18f)
                            val distance = radius * (1.1f + burstEase * (2.0f + (index % 4) * 0.35f))
                            val point = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance)
                            drawCircle(
                                Color.White.copy(alpha = (1f - burstProgress) * 0.85f),
                                radius =
                                    (
                                        1.5f +
                                            index % 3
                                    ).dp.toPx(),
                                center = point,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BreathControlPreview() {
    BreathControl(
        enabled = true,
        onExplosionFinished = {},
        onMicrophoneError = {},
        ensureLocationPermission = { true },
        onPhaseChanged = {},
        cancelSignal = 0,
        requestPermissionOnLaunch = true,
    )
}

private fun BreathSessionState.isInputActive(): Boolean =
    this is BreathSessionState.Listening ||
        this is BreathSessionState.NeedsMore ||
        this is BreathSessionState.Quiet
