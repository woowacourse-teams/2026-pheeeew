package com.pheeeew.feature.screens.pressevent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.AppThemeColors
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.feature.emotion.component.EventPressPad
import com.pheeeew.feature.emotion.component.formatCount
import com.pheeeew.feature.emotion.component.rememberTapReducedMotion
import com.pheeeew.feature.emotion.model.EmotionCountUiModel
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.TimeSource

internal object EventPressColors {
    val Background = Color(0xFF3E3E47)
    val Ink = Color.White
    val Surface = Color(0xFF1F1F29)
    val SurfaceBorder = Color(0xFF3B3B4A)
    val MutedInk = Color(0xFFB9B9C6)
}

private const val RESULT_OVERLAY_MIN_MILLIS = 2_000L
private const val WAVE_DURATION_NANOS = 500_000_000L
private const val MAX_WAVES = 12
private const val COUNTER_POP_SCALE = 1.18f
private const val COUNTER_MAX_POP_SCALE = 1.6f
private const val COUNTER_RAPID_POP_STEP = 0.14f
private const val COUNTER_RAPID_THRESHOLD = 1.05f
private const val COUNTER_SHAKE_MILLIS = 280
private const val COUNTER_SHAKE_CYCLES = 3f
private const val COUNTER_SHAKE_DEGREES = 4f
private const val COUNTER_SHAKE_OFFSET_DP = 6f
private const val WAVE_RADIUS_FACTOR = 1.3f
private const val WAVE_COLOR_RISE = 0.3f

private class PressWave(
    val color: Color,
    val origin: Offset,
    val buttonRadius: Float,
    val startNanos: Long,
)

@Composable
internal fun PressEventScreen(
    state: PressEventUiState,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onPress: (EmotionKind) -> Boolean,
    submission: EventSubmissionStatus = EventSubmissionStatus.None,
    result: PressEventAttemptResult? = null,
    onRetrySubmission: () -> Unit = {},
    faceVisuals: Map<EmotionKind, EmotionFaceVisual> = emptyMap(),
    timer: @Composable () -> Unit = { EventTimer(20_000L, EventPressColors.Ink) },
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    val reducedMotion = rememberTapReducedMotion()
    val counts =
        remember(state.counts) {
            EmotionKind.entries.map { EmotionCountUiModel(it, state.counts[it] ?: 0L) }
        }
    val waves = remember { mutableStateListOf<PressWave>() }
    val waveFrame = remember { mutableLongStateOf(0L) }
    val clock = remember { TimeSource.Monotonic.markNow() }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    val palette = MaterialTheme.appColors.emotion.tapPalette
    LaunchedEffect(waves) {
        snapshotFlow { waves.isNotEmpty() }.collectLatest { active ->
            while (active) {
                withFrameNanos { }
                val now = clock.elapsedNow().inWholeNanoseconds
                waves.removeAll { now - it.startNanos >= WAVE_DURATION_NANOS }
                waveFrame.longValue = now
                if (waves.isEmpty()) break
            }
        }
    }
    val displayedFaces = remember(state.counts, faceVisuals) { faceVisuals.ifEmpty { eventFaceVisuals(state.counts) } }
    Box(
        modifier
            .fillMaxSize()
            .background(EventPressColors.Background)
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .drawBehind {
                val now = waveFrame.longValue
                waves.forEach { wave ->
                    val t = ((now - wave.startNanos).toFloat() / WAVE_DURATION_NANOS).coerceIn(0f, 1f)
                    val radius = wave.buttonRadius * WAVE_RADIUS_FACTOR
                    val center = wave.origin - rootOffset
                    val whiteAlpha = 1f - t * t * t
                    val colorAlpha =
                        if (t < WAVE_COLOR_RISE) {
                            t / WAVE_COLOR_RISE
                        } else {
                            val rest = (t - WAVE_COLOR_RISE) / (1f - WAVE_COLOR_RISE)
                            1f - rest * rest
                        }
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colorStops =
                                    arrayOf(
                                        0f to wave.color,
                                        0.92f to wave.color,
                                        1f to wave.color.copy(alpha = 0f),
                                    ),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                        alpha = colorAlpha,
                    )
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colorStops =
                                    arrayOf(
                                        0f to Color.White,
                                        0.82f to Color.White,
                                        0.92f to Color.White.copy(alpha = 0f),
                                    ),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                        alpha = whiteAlpha,
                    )
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                EventBackButton(onBack, EventPressColors.Ink)
            }
            timer()
            EventTotalCounter(state.total, reducedMotion)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val padWidth =
                    ((maxHeight.value / 345f * 354f + 32f).dp)
                        .coerceIn(200.dp.coerceAtMost(maxWidth), maxWidth)
                EventPressPad(
                    counts = counts,
                    faceVisuals = displayedFaces,
                    enabled = state.phase == PressEventPhase.Playing,
                    labelColor = EventPressColors.Ink,
                    countColor = EventPressColors.MutedInk,
                    onEmotionTap = onPress,
                    onPressOrigin = { kind, origin, buttonRadius ->
                        if (!reducedMotion) {
                            if (waves.size >= MAX_WAVES) waves.removeAt(0)
                            waves +=
                                PressWave(
                                    kind.waveColor(palette),
                                    origin,
                                    buttonRadius,
                                    clock.elapsedNow().inWholeNanoseconds,
                                )
                        }
                    },
                    modifier = Modifier.width(padWidth).padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        if (state.phase == PressEventPhase.Countdown) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "20초 동안 마음껏 누르세요!",
                    fontFamily = font,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "${state.seconds}",
                    fontFamily = font,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
        }
    }
    val finished = state.phase == PressEventPhase.Finished
    var minOverlayElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(finished) {
        minOverlayElapsed = false
        if (finished) {
            delay(RESULT_OVERLAY_MIN_MILLIS)
            minOverlayElapsed = true
        }
    }
    val resultPending = submission == EventSubmissionStatus.None || submission == EventSubmissionStatus.Submitting
    val overlayVisible = finished && (!minOverlayElapsed || resultPending)
    if (overlayVisible) EventResultOverlay()
    if (finished && !overlayVisible) {
        PressEventResultDialog(
            total = state.total,
            status = submission,
            result = result,
            onRetrySubmission = onRetrySubmission,
            onRetry = onStart,
            onReturn = onBack,
        )
    }
}

@Composable
private fun EventResultOverlay() {
    val font = notoSansKrFontFamily()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("끝", fontFamily = font, fontSize = 72.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(24.dp))
        CircularLoadingIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "결과 받아오는 중...",
            fontFamily = font,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun EventTotalCounter(
    total: Long,
    reducedMotion: Boolean,
) {
    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    var previous by remember { mutableLongStateOf(total) }
    LaunchedEffect(total) {
        if (total > previous && !reducedMotion) {
            val peak =
                if (scale.value > COUNTER_RAPID_THRESHOLD) {
                    minOf(COUNTER_MAX_POP_SCALE, scale.value + COUNTER_RAPID_POP_STEP)
                } else {
                    COUNTER_POP_SCALE
                }
            if (peak >= COUNTER_MAX_POP_SCALE) {
                launch {
                    shake.snapTo(1f)
                    shake.animateTo(0f, tween(COUNTER_SHAKE_MILLIS, easing = LinearEasing))
                }
            }
            scale.animateTo(peak, tween(40, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        }
        previous = total
    }
    Text(
        formatCount(total),
        modifier =
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                val strength = shake.value
                if (strength > 0f) {
                    val phase = (1f - strength) * COUNTER_SHAKE_CYCLES * 2f * PI.toFloat()
                    rotationZ = sin(phase) * COUNTER_SHAKE_DEGREES * strength
                    translationX = sin(phase * 1.3f) * COUNTER_SHAKE_OFFSET_DP.dp.toPx() * strength
                }
            },
        fontFamily = notoSansKrFontFamily(),
        fontSize = 52.sp,
        fontWeight = FontWeight.Black,
        color = EventPressColors.Ink,
    )
}

private fun EmotionKind.waveColor(palette: AppThemeColors.Emotion.TapPalette): Color =
    when (this) {
        EmotionKind.Blocked -> palette.frustrated
        EmotionKind.Annoyed -> palette.annoyed
        EmotionKind.Tired -> palette.tired
        EmotionKind.Defeated -> palette.discouraged
        EmotionKind.Angry -> palette.angry
    }

@Preview(name = "이벤트 카운트다운", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventCountdownPreview() {
    AppTheme { PressEventScreen(PressEventUiState(PressEventPhase.Countdown, 3), {}, {}, { false }) }
}

@Preview(name = "이벤트 진행", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventPlayingPreview() {
    AppTheme {
        PressEventScreen(
            state = PressEventUiState(PressEventPhase.Playing, 12, mapOf(EmotionKind.Angry to 42L)),
            onStart = {},
            onBack = {},
            onPress = { true },
            timer = { EventTimer(12_340L, EventPressColors.Ink) },
        )
    }
}

@Preview(name = "이벤트 결과", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventFinishedPreview() {
    AppTheme {
        PressEventScreen(
            state = PressEventUiState(PressEventPhase.Finished, 0, mapOf(EmotionKind.Angry to 123L)),
            onStart = {},
            onBack = {},
            onPress = { false },
            timer = { EventTimer(0L, EventPressColors.Ink) },
        )
    }
}
