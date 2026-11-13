package com.pheeeew.feature.emotion.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.painterResource

private const val BURST_PEAK_SCALE = 1.04f
private const val BURST_RISE_MILLIS = 70
private const val BURST_SETTLE_MILLIS = 150
private const val STAGE_FADE_MILLIS = 90

/** No input callback, automatic stage advancement, or delayed reset of the caller's state. */
@Composable
internal fun PressFaceArtwork(
    kind: EmotionKind,
    visual: EmotionFaceVisual,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { started = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { started = false }
    val inspecting = LocalInspectionMode.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val pulse = remember { Animatable(1f) }
    val playback = remember { PressFacePlayback() }
    LaunchedEffect(visual, focused, started, reducedMotion) {
        val play = playback.shouldPlay(visual, started && focused && !reducedMotion && !inspecting)
        pulse.snapTo(1f)
        if (play) {
            // Proposed visual timing only; this does not define an input threshold or reset time.
            pulse.animateTo(BURST_PEAK_SCALE, tween(BURST_RISE_MILLIS))
            pulse.animateTo(1f, tween(BURST_SETTLE_MILLIS))
        }
    }
    Box(
        modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        },
    ) {
        if (reducedMotion || !started || !focused || inspecting) {
            Image(painterResource(PressFaceCatalog.resource(kind, visual.stage)), null, Modifier.fillMaxSize())
        } else {
            AnimatedContent(
                visual.stage,
                transitionSpec = { fadeIn(tween(STAGE_FADE_MILLIS)) togetherWith fadeOut(tween(STAGE_FADE_MILLIS)) },
                label = "press-face-stage",
            ) { stage ->
                Image(painterResource(PressFaceCatalog.resource(kind, stage)), null, Modifier.fillMaxSize())
            }
        }
    }
}

/** Consume hidden/disabled transitions too, so returning to a screen never replays an old burst. */
internal class PressFacePlayback {
    private var lastVisual: EmotionFaceVisual? = null

    fun shouldPlay(
        visual: EmotionFaceVisual,
        enabled: Boolean,
    ): Boolean {
        val changed = visual != lastVisual
        lastVisual = visual
        return enabled && changed && visual.stage == EmotionFaceStage.Burst
    }
}
