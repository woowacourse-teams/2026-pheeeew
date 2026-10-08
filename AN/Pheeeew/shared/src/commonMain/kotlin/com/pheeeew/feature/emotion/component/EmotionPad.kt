package com.pheeeew.feature.emotion.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.emotion.model.EmotionCountUiModel
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_feedback_plus_one
import kotlin.time.TimeSource

/** Five emotion buttons and the independent reactions created by accepted presses. */
@Composable
internal fun EmotionPad(
    counts: List<EmotionCountUiModel>,
    optimisticPressCounts: Map<EmotionKind, Long> = emptyMap(),
    countPlaceholder: String? = null,
    enabled: Boolean,
    onEmotionTap: (EmotionKind) -> Boolean,
    arrangement: EmotionPadArrangement = EmotionPadArrangement.ThreeTwo,
    allowVisualFeedbackWhenRejected: Boolean = false,
    feedbackAcknowledgementForAcceptedPress: () -> (() -> Unit)? = { null },
    preserveFeedbackWhileDisabled: Boolean = false,
    modifier: Modifier = Modifier,
    reducedMotionOverride: Boolean? = null,
) {
    val reducedMotion = reducedMotionOverride ?: rememberTapReducedMotion()
    val mark = remember { TimeSource.Monotonic.markNow() }

    fun now(): Double = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0

    val feedback = remember { TapFeedbackState<EmotionKind>() }
    val motions = remember { EmotionKind.entries.associateWith { TapButtonMotion() } }
    val random = remember { TapRandom() }
    var tick by remember { mutableIntStateOf(0) }
    // Read the frame clock only inside graphicsLayer to avoid recomposing the pad per frame.
    val frameTime = remember { mutableStateOf(0.0) }
    val frameRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    val padPositionState = remember { mutableStateOf(Offset.Zero) }
    var padPosition by padPositionState
    var rootWidth by remember { mutableStateOf(402.0) }
    val density = LocalDensity.current
    val font = notoSansKrFontFamily()
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val reduce = reducedMotion || scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val focused = LocalWindowInfo.current.isWindowFocused
    val currentFocused by rememberUpdatedState(focused)
    val feedbackAcknowledgement by rememberUpdatedState(feedbackAcknowledgementForAcceptedPress)
    val callback by rememberUpdatedState(onEmotionTap)
    val hapticFeedback = rememberEmotionHapticFeedback()
    val byKind = remember(counts) { counts.associateBy { it.kind } }

    fun refresh() {
        frameTime.value = now()
        tick++
        frameRequests.trySend(Unit)
    }

    fun settle() {
        feedback.clear()
        motions.values.forEach { it.reset() }
        frameTime.value = now()
        tick++
    }

    LaunchedEffect(enabled, preserveFeedbackWhileDisabled) {
        if (!enabled && !preserveFeedbackWhileDisabled) {
            feedback.clear()
            tick++
        }
    }
    LaunchedEffect(reduce, focused) {
        if (reduce || !focused) settle()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { settle() }
    DisposableEffect(Unit) {
        onDispose {
            feedback.clear()
            motions.values.forEach { it.reset() }
        }
    }
    // Keep one consumer alive: a new tap must wake it even as the last animation finishes.
    // A Boolean effect key can lose a false -> true transition within one composition.
    LaunchedEffect(frameRequests) {
        for (request in frameRequests) {
            do {
                withFrameNanos { }
                val time = now()
                val previousParticleCount = feedback.particles.size
                feedback.advance(time)
                motions.values.forEach { it.sample(time) }
                frameTime.value = time
                if (feedback.particles.size != previousParticleCount) tick++
            } while (
                isActive &&
                (feedback.particles.isNotEmpty() || motions.values.any { it.isRunning(frameTime.value) })
            )
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("emotion-pad")
                .onGloballyPositioned { coordinates ->
                    padPositionState.value = coordinates.positionInRoot() / density.density
                    rootWidth =
                        coordinates.findRootCoordinates().size.width / density.density.toDouble()
                },
    ) {
        val unit = (maxWidth.value / 354f).coerceAtMost(1f)
        val inset = (maxWidth.value - 354 * unit) / 2
        val particles = remember(tick) { feedback.particles.toList() }
        val localizedTapTexts = TapCatalog.localizedTexts()
        val plusOne = stringResource(Res.string.emotion_feedback_plus_one)
        val origins = arrangement.buttonOrigins

        fun rootPoint(
            originX: Float,
            originY: Float,
            pointer: Offset?,
        ): TapPoint? {
            if (pointer == null) return null
            return TapPoint(
                (padPosition.x + originX + pointer.x / density.density).toDouble(),
                (padPosition.y + originY + pointer.y / density.density).toDouble(),
            )
        }

        fun addReaction(
            kind: EmotionKind,
            originX: Float,
            originY: Float,
            pointer: Offset?,
        ) {
            val reaction = TapCatalog.pick(kind, random, localizedTapTexts, plusOne)
            val measured =
                measurer
                    .measure(
                        reaction.value,
                        stickerStyle(reaction, font),
                        maxLines = 1,
                        softWrap = false,
                    ).size
            val width =
                when (reaction.kind) {
                    TapReactionKind.Face -> 52.0
                    TapReactionKind.Emoji -> measured.width / density.density.toDouble()
                    TapReactionKind.Text -> measured.width / density.density + 28.0
                    TapReactionKind.Plus -> measured.width / density.density + 22.0
                }
            val height =
                when (reaction.kind) {
                    TapReactionKind.Face -> 54.0
                    TapReactionKind.Emoji -> 42.0 * density.fontScale
                    TapReactionKind.Text -> 20.9 * density.fontScale + 18
                    TapReactionKind.Plus -> 20.9 * density.fontScale + 17
                }
            val button =
                TapRect(
                    left = (padPosition.x + originX).toDouble(),
                    top = (padPosition.y + originY).toDouble(),
                    width = 105.0 * unit,
                    height = 109.4277 * unit,
                )
            val timeOfTap = now()
            val combo = feedback.combo(kind, timeOfTap)
            val flight =
                TapTrajectory.create(
                    random = random,
                    button = button,
                    pointer = rootPoint(originX, originY, pointer),
                    viewportWidth = rootWidth,
                    width = width,
                    height = height,
                    combo = combo,
                    reduced = reduce,
                )
            feedback.add(kind, reaction, flight, timeOfTap, width, height)
            refresh()
        }

        Box(Modifier.fillMaxWidth().height((345 * unit).dp)) {
            EmotionKind.entries.forEachIndexed { index, kind ->
                val emotionLabel = stringResource(EmotionFeedbackCatalog.name(kind))
                val motion = motions.getValue(kind)
                val origin = origins[index]
                val originX = origin.x
                val originY = origin.y
                val x = inset + originX * unit
                val y = originY * unit
                val count = (byKind[kind]?.count ?: 0L) + (optimisticPressCounts[kind] ?: 0L)

                EmotionPadButton(
                    kind = kind,
                    emotionLabel = emotionLabel,
                    count = count,
                    countPlaceholder = countPlaceholder,
                    unit = unit,
                    enabled = enabled,
                    reducedMotion = reduce,
                    motion = motion,
                    frameTime = frameTime,
                    font = font,
                    now = ::now,
                    onMotionChanged = ::refresh,
                    onTap = { pointer ->
                        val accepted = callback(kind)
                        dispatchEmotionTapFeedback(
                            accepted = accepted,
                            allowVisualFeedbackWhenRejected = allowVisualFeedbackWhenRejected,
                            hapticFeedback = hapticFeedback,
                        ) {
                            // Start visual feedback now; the ViewModel persists accepted taps asynchronously.
                            addReaction(kind, originX, originY, pointer)
                            val acknowledge = feedbackAcknowledgement()
                            if (acknowledge != null) {
                                scope.launch {
                                    withFrameNanos { }
                                    if (currentFocused) acknowledge()
                                }
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .offset(x.dp, y.dp)
                            .size((105 * unit).dp, (168 * unit).dp),
                )
            }
            EmotionFeedbackLayer(
                particles = particles,
                frameTime = frameTime,
                padPosition = padPositionState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun formatCount(value: Long): String =
    value
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

@Preview(widthDp = 354, heightDp = 345, name = "그룹 감정 버튼")
@Composable
private fun EmotionPadPreview() {
    EmotionPad(
        counts = EmotionKind.entries.mapIndexed { index, kind -> EmotionCountUiModel(kind, (index + 1) * 123L) },
        enabled = true,
        onEmotionTap = { true },
    )
}
