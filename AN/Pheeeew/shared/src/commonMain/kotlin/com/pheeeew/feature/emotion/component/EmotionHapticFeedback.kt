package com.pheeeew.feature.emotion.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.core.haptics.JindongEmotionHapticController
import com.pheeeew.core.haptics.USE_JINDONG_HAPTICS
import io.github.compose.jindong.core.executor.createHapticExecutor

@Composable
internal fun rememberEmotionHapticFeedback(): () -> Unit {
    if (!USE_JINDONG_HAPTICS) return rememberNativeEmotionHapticFeedback()

    val context = rememberEmotionHapticContext()
    val playbackAllowed = rememberEmotionHapticPlaybackAllowed()
    val lifecycleOwner = LocalLifecycleOwner.current
    val owner = remember(lifecycleOwner) { Any() }
    val controller = JindongEmotionHapticController.shared
    DisposableEffect(controller, lifecycleOwner, owner, context) {
        fun activate() = controller.activate(owner) { createHapticExecutor(context) }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        activate()
                    }

                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                        controller.deactivate(owner)
                    }

                    else -> {
                        Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) activate()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.deactivate(owner)
        }
    }
    return remember(controller, owner, playbackAllowed) {
        { if (playbackAllowed()) controller.play(owner) }
    }
}

@Composable
internal expect fun rememberNativeEmotionHapticFeedback(): () -> Unit

@Composable
internal expect fun rememberEmotionHapticContext(): Any?

@Composable
internal expect fun rememberEmotionHapticPlaybackAllowed(): () -> Boolean

internal inline fun dispatchEmotionTapFeedback(
    accepted: Boolean,
    allowVisualFeedbackWhenRejected: Boolean,
    hapticFeedback: () -> Unit,
    visualFeedback: () -> Unit,
) {
    if (accepted && !allowVisualFeedbackWhenRejected) hapticFeedback()
    if (accepted || allowVisualFeedbackWhenRejected) visualFeedback()
}
