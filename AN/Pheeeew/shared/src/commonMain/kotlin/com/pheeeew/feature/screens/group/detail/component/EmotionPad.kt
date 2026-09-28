package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_emotion_button_base
import kotlin.time.TimeSource

/** Five emotion buttons and the independent reactions created by accepted presses. */
@Composable
internal fun EmotionPad(
    counts: List<EmotionCountUiModel>,
    optimisticPressCounts: Map<EmotionKind, Long> = emptyMap(),
    enabled: Boolean,
    onEmotionTap: (EmotionKind) -> Boolean,
    fixtureFeedbackOnAcceptedPress: Boolean = false,
    feedbackOperationKey: () -> com.pheeeew.feature.screens.group.model.GroupOperationKey? = { null },
    onFeedbackShown: (com.pheeeew.feature.screens.group.model.GroupOperationKey) -> Unit = {},
    preserveFeedbackWhileDisabled: Boolean = false,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberTapReducedMotion(),
) {
    val mark = remember { TimeSource.Monotonic.markNow() }

    fun now(): Double = mark.elapsedNow().inWholeNanoseconds / 1_000_000.0

    val feedback = remember { TapFeedbackState<EmotionKind>() }
    val motions = remember { EmotionKind.entries.associateWith { TapButtonMotion() } }
    val random = remember { TapRandom() }
    var tick by remember { mutableIntStateOf(0) }
    // Read the frame clock only inside graphicsLayer to avoid recomposing the pad per frame.
    val frameTime = remember { mutableStateOf(0.0) }
    val frameRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    var padPosition by remember { mutableStateOf(Offset.Zero) }
    var rootWidth by remember { mutableStateOf(402.0) }
    val density = LocalDensity.current
    val font = notoSansKrFontFamily()
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val reduce = reducedMotion || scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val focused = LocalWindowInfo.current.isWindowFocused
    val currentFocused by rememberUpdatedState(focused)
    val operationKeyForFeedback by rememberUpdatedState(feedbackOperationKey)
    val feedbackShown by rememberUpdatedState(onFeedbackShown)
    val callback by rememberUpdatedState(onEmotionTap)
    val inputEnabled by rememberUpdatedState(enabled)
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
                    padPosition = coordinates.positionInRoot() / density.density
                    rootWidth =
                        coordinates.findRootCoordinates().size.width / density.density.toDouble()
                },
    ) {
        val unit = (maxWidth.value / 354f).coerceAtMost(1f)
        val inset = (maxWidth.value - 354 * unit) / 2
        val particles = remember(tick) { feedback.particles.toList() }
        val origins = listOf(0f to 0f, 124.5f to 0f, 249f to 0f, 62.25f to 177f, 186.75f to 177f)

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
            val reaction = TapCatalog.pick(kind, random)
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
                val emotion = TapCatalog.emotion(kind)
                val motion = motions.getValue(kind)
                val (originX, originY) = origins[index]
                val x = inset + originX * unit
                val y = originY * unit
                var pointer by remember(kind) { mutableStateOf<Offset?>(null) }
                val interactions = remember(kind) { MutableInteractionSource() }
                val keys = remember(kind) { mutableSetOf<Key>() }
                var keyboardActivation by remember(kind) { mutableStateOf(false) }

                LaunchedEffect(enabled, reduce, focused) {
                    keys.clear()
                    keyboardActivation = false
                    pointer = null
                }

                val count = (byKind[kind]?.count ?: 0L) + (optimisticPressCounts[kind] ?: 0L)
                val buttonModifier =
                    Modifier
                        .size((105 * unit).dp, (110 * unit).dp)
                        .testTag("emotion-${kind.name}")
                        .pointerInput(reduce) {
                            try {
                                awaitEachGesture {
                                    val down =
                                        awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                    if (!inputEnabled) {
                                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                        return@awaitEachGesture
                                    }
                                    pointer = down.position
                                    motion.press(now(), reduce)
                                    refresh()
                                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                    motion.release(now(), reduce)
                                    if (up == null) pointer = null
                                    refresh()
                                }
                            } finally {
                                motion.reset()
                                pointer = null
                            }
                        }.onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                keys.clear()
                                keyboardActivation = false
                                motion.release(now(), reduce)
                                refresh()
                            }
                        }.onPreviewKeyEvent { event ->
                            val activation =
                                event.key in
                                    setOf(
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.Spacebar,
                                        Key.DirectionCenter,
                                    )
                            if (!enabled || !activation) {
                                false
                            } else {
                                when (event.type) {
                                    KeyEventType.KeyDown -> {
                                        if (!keys.add(event.key)) {
                                            true
                                        } else {
                                            keyboardActivation = true
                                            motion.press(now(), reduce)
                                            refresh()
                                            false
                                        }
                                    }

                                    KeyEventType.KeyUp -> {
                                        keys.remove(event.key)
                                        if (keys.isEmpty()) motion.release(now(), reduce)
                                        refresh()
                                        false
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            }
                        }.clickable(
                            interactionSource = interactions,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                        ) {
                            if (pointer == null && !keyboardActivation && !motion.pressed) {
                                motion.tap(now(), reduce)
                            } else {
                                motion.release(now(), reduce)
                            }
                            if (callback(kind) || fixtureFeedbackOnAcceptedPress) {
                                // Start visual feedback now; the ViewModel persists accepted taps asynchronously.
                                addReaction(kind, x, y, pointer)
                                val operationKey = operationKeyForFeedback()
                                if (operationKey != null) {
                                    scope.launch {
                                        withFrameNanos { }
                                        if (currentFocused) feedbackShown(operationKey)
                                    }
                                }
                            }
                            pointer = null
                            keyboardActivation = false
                            refresh()
                        }.semantics {
                            contentDescription = "${emotion.label} 표현하기, ${formatCount(count)}번"
                        }

                Box(Modifier.offset(x.dp, y.dp).size((105 * unit).dp, (168 * unit).dp)) {
                    Box(
                        modifier = buttonModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.group_emotion_button_base),
                            contentDescription = null,
                            modifier = Modifier.size((105 * unit).dp, (110 * unit).dp),
                        )
                        Image(
                            painter = painterResource(EmotionFeedbackCatalog.buttonFace(kind)),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .requiredSize((110 * unit).dp, (115 * unit).dp)
                                    .graphicsLayer {
                                        val transform = motion.sample(frameTime.value)
                                        transformOrigin = TransformOrigin(.5f, .8f)
                                        // Keep the idle face lifted; the 4dp press travel lands it at the base center.
                                        translationY = (transform.y.toFloat() - 4f) * density.density
                                        scaleX = transform.scale.toFloat()
                                        scaleY = scaleX
                                    }.testTag("emotion-surface-${kind.name}"),
                        )
                    }
                    Column(
                        modifier = Modifier.offset(y = (116 * unit).dp).width((105 * unit).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BasicText(
                            text = emotion.label,
                            modifier = Modifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = TapInk,
                                    fontFamily = font,
                                    fontSize = (16 * unit).sp,
                                    lineHeight = (19 * unit).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                        Spacer(Modifier.height((3.6f * unit).dp))
                        BasicText(
                            text = formatCount(count),
                            modifier = Modifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = Color(0xFF777C78),
                                    fontFamily = font,
                                    fontSize = (20 * unit).sp,
                                    lineHeight = (24 * unit).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                }
            }

            particles.forEach { particle ->
                key(particle.id) {
                    val reaction = particle.reaction
                    val emotion = TapCatalog.emotion(particle.key)
                    Box(
                        modifier =
                            Modifier
                                .requiredSize(particle.width.dp, particle.height.dp)
                                .graphicsLayer {
                                    val transform = particle.flight.sample(frameTime.value - particle.started)
                                    translationX =
                                        ((particle.flight.left - padPosition.x + transform.x) * density.density)
                                            .toFloat()
                                    translationY =
                                        ((particle.flight.top - padPosition.y + transform.y) * density.density)
                                            .toFloat()
                                    scaleX = transform.scale.toFloat()
                                    scaleY = scaleX
                                    rotationZ = transform.rotation.toFloat()
                                    alpha = transform.alpha.toFloat()
                                }.testTag("tap-particle"),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (reaction.kind) {
                            TapReactionKind.Face -> {
                                Image(painterResource(emotion.face), null, Modifier.fillMaxSize())
                            }

                            TapReactionKind.Emoji -> {
                                BasicText(reaction.value, style = stickerStyle(reaction, font))
                            }

                            TapReactionKind.Text,
                            TapReactionKind.Plus,
                            -> {
                                Canvas(Modifier.fillMaxSize()) {
                                    val d = density.density
                                    val radius = CornerRadius(12 * d)
                                    drawRoundRect(TapInk, Offset(1 * d, 4 * d), size, radius)
                                    drawRoundRect(
                                        if (reaction.kind == TapReactionKind.Plus) Color(0xFFFFE36E) else emotion.color,
                                        cornerRadius = radius,
                                    )
                                    drawRoundRect(
                                        TapInk,
                                        Offset(d, d),
                                        Size(size.width - 2 * d, size.height - 2 * d),
                                        CornerRadius(11 * d),
                                        style = Stroke(2 * d),
                                    )
                                }
                                BasicText(
                                    text = reaction.value,
                                    modifier =
                                        Modifier.offset(
                                            y = if (reaction.kind == TapReactionKind.Plus) (-.5).dp else (-1).dp,
                                        ),
                                    style = stickerStyle(reaction, font),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TapInk = Color(0xFF242725)

private fun stickerStyle(
    reaction: TapReaction,
    font: FontFamily,
): TextStyle =
    if (reaction.kind == TapReactionKind.Emoji) {
        TextStyle(fontSize = 35.sp, lineHeight = 42.sp)
    } else {
        TextStyle(
            color = TapInk,
            fontFamily = font,
            fontWeight = FontWeight.Black,
            fontSize = 19.sp,
            lineHeight = 20.9.sp,
            letterSpacing = (-.6).sp,
        )
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
