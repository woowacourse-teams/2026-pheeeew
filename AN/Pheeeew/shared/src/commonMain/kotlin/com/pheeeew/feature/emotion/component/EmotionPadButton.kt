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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_tap_accessibility
import pheeeew.shared.generated.resources.group_emotion_button_base
import pheeeew.shared.generated.resources.press_button_face_shadow
import pheeeew.shared.generated.resources.press_button_silver_base

private val activationKeys = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)
private val emotionCountColor = Color(0xFF777C78)
private val emotionInk = Color(0xFF242725)

/** Owns one button's pointer, keyboard, accessibility, and pressed-state behavior. */
@Composable
internal fun EmotionPadButton(
    kind: EmotionKind,
    faceVisual: EmotionFaceVisual? = null,
    emotionLabel: String,
    count: Long,
    countPlaceholder: String?,
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
    val countText = countPlaceholder ?: formatCount(count)
    val accessibilityDescription =
        stringResource(
            Res.string.emotion_tap_accessibility,
            emotionLabel,
            countText,
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

    fun Modifier.faceMotion(
        origin: TransformOrigin,
        restOffset: Float,
        depthShadow: Boolean = false,
    ): Modifier =
        graphicsLayer {
            val transform = motion.sample(frameTime.value)
            if (depthShadow) {
                // Raised at rest; fully hidden when the cap is compressed into the base.
                alpha = 1f - (transform.y / 3.0).toFloat().coerceIn(0f, 1f)
            }
            transformOrigin = origin
            val motionUnit = if (faceVisual == null) 1f else unit
            translationY = (transform.y.toFloat() * motionUnit + restOffset) * density.density
            scaleX = transform.scale.toFloat()
            scaleY = scaleX
        }

    fun Modifier.capDepth(): Modifier =
        drawWithContent {
            val depth = (motion.sample(frameTime.value).y / 3.0).toFloat().coerceIn(0f, 1f)
            if (depth == 0f || faceVisual == null || faceVisual.stage == EmotionFaceStage.Burst) {
                drawContent()
            } else {
                // Preserve the cap's own outline; expand the tired-face mask to include its baked blur.
                val geometry = pressFaceCapGeometry(kind, faceVisual.stage)
                val pixelUnit = size.width / PressFaceCatalog.CANVAS_SIZE
                val remainingDepth = 3f * (1f - depth)
                val outerRadius = geometry.radius + geometry.shadowSpread * depth
                val raised =
                    Path().apply {
                        addOval(
                            Rect(
                                (90f - outerRadius) * pixelUnit,
                                (93f - outerRadius) * pixelUnit,
                                (90f + outerRadius) * pixelUnit,
                                (93f + outerRadius) * pixelUnit,
                            ),
                        )
                    }
                val cap =
                    Path().apply {
                        addOval(
                            Rect(
                                (90f - geometry.radius) * pixelUnit,
                                (90f + remainingDepth - geometry.radius) * pixelUnit,
                                (90f + geometry.radius) * pixelUnit,
                                (90f + remainingDepth + geometry.radius) * pixelUnit,
                            ),
                        )
                    }
                val concealed = Path.combine(PathOperation.Difference, raised, cap)
                clipPath(concealed, ClipOp.Difference) { this@drawWithContent.drawContent() }
            }
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
                    onMotionChanged()
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
                painter =
                    painterResource(
                        if (faceVisual ==
                            null
                        ) {
                            Res.drawable.group_emotion_button_base
                        } else {
                            Res.drawable.press_button_silver_base
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.size((105 * unit).dp, (110 * unit).dp),
            )
            if (faceVisual != null) {
                Image(
                    painterResource(Res.drawable.press_button_face_shadow),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size((105 * unit).dp, (110 * unit).dp)
                            .faceMotion(
                                TransformOrigin(
                                    .5f,
                                    (55f + PressFaceCatalog.CANVAS_SIZE * .26f - 4f / unit) / 110f,
                                ),
                                restOffset = 0f,
                                depthShadow = true,
                            ),
                )
            }
            val faceModifier =
                Modifier
                    .requiredSize(
                        ((if (faceVisual == null) 110 else PressFaceCatalog.CANVAS_SIZE) * unit).dp,
                        ((if (faceVisual == null) 115 else PressFaceCatalog.CANVAS_SIZE) * unit).dp,
                    ).faceMotion(
                        // Both layers pivot at the face's lower edge in button coordinates.
                        if (faceVisual == null) TransformOrigin(.5f, .8f) else TransformOrigin(.5f, .76f),
                        restOffset = -4f,
                    ).capDepth()
                    .testTag("emotion-surface-${kind.name}")
            if (faceVisual == null) {
                Image(painterResource(EmotionFeedbackCatalog.buttonFace(kind)), null, faceModifier)
            } else {
                PressFaceArtwork(kind, faceVisual, reducedMotion, faceModifier)
            }
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
                text = countText,
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
