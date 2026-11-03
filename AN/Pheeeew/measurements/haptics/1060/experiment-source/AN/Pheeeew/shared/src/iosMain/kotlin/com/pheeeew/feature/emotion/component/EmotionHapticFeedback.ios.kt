package com.pheeeew.feature.emotion.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
@Composable
internal actual fun rememberEmotionHapticFeedback(): () -> Unit {
    val generator = remember { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleMedium) }
    val experiment = remember(generator) { if (Platform.isDebugBinary) PressHapticExperiment.fromEnvironment() else null }
    LaunchedEffect(generator, experiment) {
        experiment?.run(generator)
    }
    return remember(generator, experiment) {
        {
            if (experiment == null) generator.impactOccurred()
            else println("[HapticExperiment] manualHapticSkipped=true; wait for automatic run")
        }
    }
}
