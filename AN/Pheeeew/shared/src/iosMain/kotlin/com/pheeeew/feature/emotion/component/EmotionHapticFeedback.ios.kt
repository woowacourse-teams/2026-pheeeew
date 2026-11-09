package com.pheeeew.feature.emotion.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.core.di.IosGraphs
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState.UIApplicationStateActive

@Composable
internal actual fun rememberNativeEmotionHapticFeedback(): () -> Unit {
    val controller = IosGraphs.app.emotionHaptics
    val lifecycleOwner = LocalLifecycleOwner.current
    val owner = remember(controller, lifecycleOwner) { Any() }
    DisposableEffect(controller, lifecycleOwner, owner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        controller.activate(owner)
                    }

                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                        controller
                            .deactivate(
                                owner,
                            )
                    }

                    else -> {
                        Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.activate(owner)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.deactivate(owner)
        }
    }
    return remember(controller, owner) { { controller.play(owner) } }
}

@Composable
internal actual fun rememberEmotionHapticContext(): Any? = null

@Composable
internal actual fun rememberEmotionHapticPlaybackAllowed(): () -> Boolean =
    remember { { UIApplication.sharedApplication.applicationState == UIApplicationStateActive } }
