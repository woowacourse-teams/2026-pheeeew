package com.pheeeew.feature.emotion.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_tap_accessibility
import pheeeew.shared.generated.resources.group_emotion_button_base

private val activationKeys = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)
private val emotionCountColor = Color(0xFF777C78)
private val emotionInk = Color(0xFF242725)

/** Owns one button's pointer, keyboard, accessibility, and pressed-state behavior. */
@Composable
internal fun EmotionPadButton(
    kind: EmotionKind,
    emotionLabel: String,
    count: Long,
    unit: Float,
    enabled: Boolean,
    reducedMotion: Boolean,
    motion: TapButtonMotion,
    frameTime: State<Double>,
    font: FontFamily,
    now: () -> Double,
    onMotionChanged: () -> Unit,
    onTap: (Offset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val inputEnabled by rememberUpdatedState(enabled)
    var pointer by remember(kind) { mutableStateOf<Offset?>(null) }
    val interactions = remember(kind) { MutableInteractionSource() }
    val pressedKeys = remember(kind) { mutableSetOf<Key>() }
    var keyboardActivation by remember(kind) { mutableStateOf(false) }
    val accessibilityDescription =
        stringResource(
            Res.string.emotion_tap_accessibility,
            emotionLabel,
            formatCount(count),
        )

    LaunchedEffect(enabled, reducedMotion, focused) {
        pressedKeys.clear()
        keyboardActivation = false
        pointer = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        pressedKeys.clear()
        keyboardActivation = false
        pointer = null
    }

    val buttonModifier =
        Modifier
            .size((105 * unit).dp, (110 * unit).dp)
            .testTag("emotion-${kind.name}")
            .pointerInput(reducedMotion) {
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
                        motion.press(now(), reducedMotion)
                        onMotionChanged()
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        motion.release(now(), reducedMotion)
                        if (up == null) pointer = null
                        onMotionChanged()
                    }
                } finally {
                    motion.reset()
                    pointer = null
                }
            }.onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    pressedKeys.clear()
                    keyboardActivation = false
                    motion.release(now(), reducedMotion)
                    onMotionChanged()
                }
            }.onPreviewKeyEvent { event ->
                if (!enabled || event.key !in activationKeys) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (!pressedKeys.add(event.key)) {
                                true
                            } else {
                                keyboardActivation = true
                                motion.press(now(), reducedMotion)
                                onMotionChanged()
                                false
                            }
                        }

                        KeyEventType.KeyUp -> {
                            pressedKeys.remove(event.key)
                            if (pressedKeys.isEmpty()) motion.release(now(), reducedMotion)
                            onMotionChanged()
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
                val tapPosition = pointer
                if (tapPosition == null && !keyboardActivation && !motion.pressed) {
                    motion.tap(now(), reducedMotion)
                } else {
                    motion.release(now(), reducedMotion)
                }
                onTap(tapPosition)
                pointer = null
                keyboardActivation = false
                onMotionChanged()
            }.semantics { contentDescription = accessibilityDescription }

    Box(modifier = modifier) {
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
                text = emotionLabel,
                modifier = Modifier.fillMaxWidth(),
                style =
                    TextStyle(
                        color = emotionInk,
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
                        color = emotionCountColor,
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
